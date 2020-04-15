package dev.whyoleg.ktd.cli.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.whyoleg.ktd.cli.*
import dev.whyoleg.ktd.cli.tl.*

const val pcg = "dev.whyoleg.ktd.api"

private val SerializersModuleClass = ClassName("kotlinx.serialization.modules", "SerializersModule")
private val SerialModuleClass = ClassName("kotlinx.serialization.modules", "SerialModule")

fun tdApiClasses(typedScheme: TlTypedScheme, kind: TlKind) {
    val kindName = kind.name.toLowerCase()
    val moduleName = "ktd-api-$kindName"

    typedScheme.run { objects + functions }.forEach { data ->
        file("Td${data.type}", pcg, "ktd-api", moduleName) {
            addType(tdDataType(data, data.type(typedScheme), pcg) {
                setParents(data, typedScheme, false)
            })
        }
    }
    typedScheme.sealed.forEach { (sealed, children) ->
        file("Td${sealed.type}", pcg, "ktd-api", moduleName) {
            addType(TypeSpec.classBuilder(ClassName(pcg, "Td${sealed.type}")).apply {
                addModifiers(KModifier.SEALED)
                addAnnotation(serializableAnnotation)
                sealed.kdoc(false).forEach(::addKdoc)
                setParents(sealed, typedScheme, false)
                if (sealed.type == "AuthorizationState") addSuperinterface(ClassName(pcg, "TdState"))
                addTypes(children.map { child ->
                    tdDataType(child, child.type(typedScheme), pcg) {
                        setParents(child, typedScheme, false)
                        if (child.type == "AuthorizationStateClosed") addSuperinterface(ClassName(pcg, "TdClosed"))
                        else if (child.type == "AuthorizationStateClosing") addSuperinterface(ClassName(pcg, "TdClosing"))
                    }
                })
            }.build())
        }
    }
    typedScheme.updates.forEach { (group, children) ->
        if (group == null) {
            children.map { child ->
                file("Td${child.type}", "$pcg.updates", "ktd-api", moduleName) {
                    addType(tdDataType(child, child.type(typedScheme), "$pcg.updates", false, "Td${child.type}") {
                        addSuperinterface(tdUpdateClass)
                        if (child.type == "UpdateAuthorizationState") {
                            addSuperinterface(ClassName(pcg, "TdUpdateState"))
                            addProperty(
                                PropertySpec.builder("state", ClassName(pcg, "TdState"), KModifier.OVERRIDE)
                                    .getter(FunSpec.getterBuilder().addStatement("return authorizationState").build())
                                    .build()
                            )
                        }
                    })
                }
            }
        } else {
            file("TdUpdate${group}", "$pcg.updates", "ktd-api", moduleName) {
                addType(
                    TypeSpec.classBuilder(ClassName("$pcg.updates", "TdUpdate${group}"))
                        .addModifiers(KModifier.SEALED)
                        .addAnnotation(serializableAnnotation)
                        .addSuperinterface(tdUpdateClass)
                        .addTypes(children.map { child ->
                            tdDataType(child, child.type(typedScheme), "$pcg.updates", false, child.realName(group)) {
                                superclass(ClassName("$pcg.updates", "TdUpdate${group}"))
                            }
                        })
                        .build()
                )
            }
        }
    }
}

fun TlSealedChild.realName(group: String): String {
    val newType = type.substringAfter("Update")
    return when {
        newType.startsWith("New") || newType.substringAfter(group).isBlank() -> "Data"
        else                                                                 -> newType.substringAfter(group)
    }
}

private fun CodeBlock.Builder.subclass(type: String, secondType: String? = null, pcgOverride: String? = null): CodeBlock.Builder =
    addStatement("subclass(%T.serializer())", ClassName(pcgOverride ?: pcg, "Td$type").let {
        if (secondType != null) it.nestedClass(secondType) else it
    })

private fun updatesBlock(group: String, children: List<TlSealedChild>) = CodeBlock.builder().beginControlFlow(
    "polymorphic(%T::class, %T::class, %T::class)",
    ClassName(pcg, "TdApiResponse"),
    ClassName(pcg, "TdUpdate"),
    ClassName("$pcg.updates", "TdUpdate$group")
).apply {
    children.forEach {
        val newType = it.type.substringAfter("Update")
        val overrideName = when {
            newType.startsWith("New") || newType.substringAfter(group).isBlank() -> "Data"
            else                                                                 -> newType.substringAfter(group)
        }
        subclass("Update$group", overrideName, "$pcg.updates")
    }
}.endControlFlow().build()

private fun updatesBlock(children: List<TlSealedChild>) = CodeBlock.builder().beginControlFlow(
    "polymorphic(%T::class, %T::class)",
    ClassName(pcg, "TdApiResponse"),
    ClassName(pcg, "TdUpdate")
).apply {
    children.forEach { subclass(it.type, pcgOverride = "$pcg.updates") }
}.endControlFlow().build()

private fun sealedBlock(name: String, children: List<TlSealedChild>) = CodeBlock.builder().beginControlFlow(
    "polymorphic(%T::class, %T::class)",
    ClassName(pcg, "TdApiResponse"),
    ClassName(pcg, "Td$name")
).apply {
    children.forEach { subclass(name, it.type.substringAfter(name)) }
}.endControlFlow().build()

fun builderFile(typedScheme: TlTypedScheme, kind: TlKind) {
    val kindName = kind.name.toLowerCase()
    val moduleName = "ktd-api-$kindName"
    val builderName = "${kindName}ApiBuilder"

    val isFinalKind = kind != TlKind.Core

    val controlFlow = when (isFinalKind) {
        true -> "coreApiBuilder.value + %T {"
        false -> "%T {"
    }

    val requestsBlock = typedScheme.functions.takeIfIsNotEmpty()?.let {
        CodeBlock.builder()
            .beginControlFlow("polymorphic<%T>", ClassName(pcg, "TdApiRequest"))
            .apply { it.forEach { data -> subclass(data.type) } }
            .endControlFlow()
            .build()
    }

    val allResponseBlock =
        typedScheme.sealed.mapNotNull {
            if (it.key.type(typedScheme) == TdDataType.Response) it else null
        }.takeIfIsNotEmpty()?.let {
            CodeBlock.builder().apply {
                it.forEach { (sealed, children) -> add(sealedBlock(sealed.type, children)) }
            }.build()
        }

    val responseBlock =
        typedScheme.objects.mapNotNull {
            if (it.type(typedScheme) == TdDataType.Response) it.type else null
        }.takeIfIsNotEmpty()?.let {
            CodeBlock.builder()
                .beginControlFlow("polymorphic<%T>", ClassName(pcg, "TdApiResponse"))
                .apply { it.forEach(::subclass) }
                .endControlFlow()
                .build()
        }

    val updatesBlocks = typedScheme.updates.mapNotNull { (group, children) ->
        children.takeIfIsNotEmpty()?.let {
            when (group) {
                null -> updatesBlock(it)
                else -> updatesBlock(group, it)
            }
        }
    }

    file(builderName.capitalize(), pcg, "ktd-api", moduleName) {
        if (isFinalKind) addImport("kotlinx.serialization.modules", "plus")
        addProperty(
            PropertySpec.builder(builderName, ClassName("kotlin", "Lazy").parameterizedBy(SerialModuleClass))
                .apply {
                    when {
                        isFinalKind -> {
                            addModifiers(KModifier.INTERNAL)
                            addAnnotation(suppressDeprecationError)
                        }
                        else        -> addAnnotation(deprecated("\"Used internally\"", error = true))
                    }
                }
                .initializer(
                    CodeBlock.builder()
                        .beginControlFlow("lazy {")
                        .beginControlFlow(controlFlow, SerializersModuleClass)
                        .apply {
                            requestsBlock?.let(this::add)
                            responseBlock?.let(this::add)
                            allResponseBlock?.let(this::add)
                            updatesBlocks.forEach(this::add)
                        }
                        .endControlFlow()
                        .endControlFlow()
                        .build()
                )
                .build()
        )
    }
}

fun tdApiFile(version: String, kind: TlKind) {
    val kindName = kind.name.toLowerCase()
    val moduleName = "ktd-api-$kindName"
    val apiName = "${kindName.capitalize()}TdApi"
    val builderName = "${kindName}ApiBuilder"

    file(apiName, pcg, "ktd-api", moduleName) {
        addAnnotation(suppressDeprecationError)
        addType(TypeSpec.objectBuilder(ClassName(pcg, name)).apply {
            addImport("dev.whyoleg.ktd.internal", "JsonTdApi")
            addSuperinterface(
                ClassName("dev.whyoleg.ktd", "TdApi"),
                CodeBlock.builder().addStatement("JsonTdApi(\"$version\", $builderName)").build()
            )
        }.build())
    }
}
