/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.metamodel;

import jakarta.annotation.Nonnull;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.template.GenerateMetamodel;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.lang.model.element.ElementKind.RECORD_COMPONENT;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * @since 1.2
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
public final class MetamodelProcessor extends AbstractProcessor {

    private final Set<String> generatedFiles;
    private Elements elementUtils;

    public MetamodelProcessor() {
        this.generatedFiles = new HashSet<>();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
    }

    private static boolean isRecord(Element element) {
        if (element.getKind() == RECORD) {
            return true;
        }
        if (element.getKind() == CLASS) {
            boolean isKotlinClass = element.getAnnotationMirrors().stream()
                    .anyMatch(annotation -> annotation.getAnnotationType().toString().equals("kotlin.Metadata"));
            boolean isJvmRecord = element.getAnnotationMirrors().stream()
                    .anyMatch(annotation -> annotation.getAnnotationType().toString().equals("kotlin.jvm.JvmRecord"));
            return isKotlinClass && isJvmRecord;
        }
        return false;
    }

    private static Optional<TypeMirror> getRecordComponent(Element element) {
        // Case 1: Element is a RECORD_COMPONENT (Java records).
        if (element.getKind() == RECORD_COMPONENT) {
            return ofNullable(element.asType());
        }
        // Case 2: Skip methods of RECORD elements.
        if (element.getEnclosingElement() != null && element.getEnclosingElement().getKind() == RECORD) {
            return empty();
        }
        // Case 3: Handle Kotlin methods (including @JvmRecord).
        if (element.getKind() == METHOD && element instanceof ExecutableElement method) {
            // Skip methods with parameters.
            if (!method.getParameters().isEmpty()) {
                return empty();
            }
            String methodName = method.getSimpleName().toString();
            if (Set.of("hashCode", "toString", "equals").contains(methodName)) {
                return empty();
            }
            // Exclude synthetic componentX methods unless they match a valid record field.
            if (methodName.startsWith("component")) {
                // PENDING: This may be improved in a later version.
                return empty();
            }
            return ofNullable(method.getReturnType());
        }
        return empty();
    }

    private static final Pattern LAZY_PATTERN =
            Pattern.compile("^st\\.orm\\.Lazy<([^,]+),([^>]+)>$");


    private static String extractNameIfLazy(String input) {
        Matcher matcher = LAZY_PATTERN.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return input;
    }

    private String getTypeName(Element element) {
        TypeMirror typeMirror = element.asType();
        String className = extractNameIfLazy(typeMirror.toString().substring(2));
        return getBoxedTypeName(className);
    }

    private static TypeElement asTypeElement(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return (element instanceof TypeElement) ? (TypeElement) element : null;
        }
        return null;
    }

    private static String getBoxedTypeName(String name) {
        return switch (name) {
            case "boolean" -> "java.lang.Boolean";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "char" -> "java.lang.Character";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            default -> name;
        };
    }

    private boolean implementsInterface(TypeMirror typeMirror, String interfaceName, Types types) {
        if (typeMirror == null) return false;
        Element element = types.asElement(typeMirror);
        if (element instanceof TypeElement typeElement) {
            if (typeElement.getQualifiedName().toString().equals(interfaceName)) {
                return true;
            }
            for (TypeMirror inf : typeElement.getInterfaces()) {
                if (implementsInterface(inf, interfaceName, types)) {
                    return true;
                }
            }
            // Check superclass.
            return implementsInterface(typeElement.getSuperclass(), interfaceName, types);
        }
        return false;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(NOTE, "Storm Metamodel Processor is running.");
        for (Element element : roundEnv.getRootElements()) {
            if (!isRecord(element)) {
                continue;
            }
            // Skip nested records.
            if (element.getEnclosingElement().getKind() == CLASS) {
                continue;
            }
            // Check if it’s annotated with @GenerateMetamodel.
            boolean hasGenerateMetamodel = element.getAnnotation(GenerateMetamodel.class) != null;
            // Check if it implements Entity or Projection.
            boolean implementsEntity = implementsInterface(element.asType(), Entity.class.getName(), processingEnv.getTypeUtils());
            boolean implementsProjection = implementsInterface(element.asType(), Projection.class.getName(), processingEnv.getTypeUtils());
            // Only generate if it’s annotated OR implements one of those interfaces.
            if (hasGenerateMetamodel || implementsEntity || implementsProjection) {
                generateMetamodelInterface(element);
            }
        }
        return true;
    }

    private static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0 && Character.isLowerCase(chars[i - 1])) {
                    result.append('_');
                } else if (i > 0 && Character.isUpperCase(chars[i - 1])
                        && i + 1 < chars.length && Character.isLowerCase(chars[i + 1])) {
                    result.append('_');
                }
            }
            result.append(Character.toUpperCase(c));
        }
        return result.toString();
    }

    private String buildInterfaceFields(@Nonnull Element recordElement) {
        StringBuilder builder = new StringBuilder();
        String recordName = recordElement.getSimpleName().toString();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponent(enclosed).orElse(null);
            if (recordComponent != null) {
                TypeElement type = asTypeElement(recordComponent);
                if (type != null && isRecord(type)) {
                    if (type.getEnclosingElement().getKind() == CLASS) {
                        continue;   // Skip nested records.
                    }
                    // Ensure generation of interfaces that may lie in different packages that would not be
                    // picked up by the annotation processor.
                    generateMetamodelInterface(type);
                    String fieldName = enclosed.getSimpleName().toString();
                    builder.append("    ").append(type.getQualifiedName()).append("Metamodel<").append(recordName).append("> ").append(fieldName)
                            .append(" = new ").append(type.getQualifiedName()).append("Metamodel<>(").append("\"").append(fieldName).append("\"").append(");\n");
                } else {
                    String fieldName = enclosed.getSimpleName().toString();
                    String fieldType = getTypeName(enclosed);
                    builder.append("    Metamodel<").append(recordName).append(", ").append(fieldType).append("> ").append(fieldName).append(" = new ").append(recordName).append("Metamodel<")
                            .append(recordName).append(">().").append(fieldName).append(";\n");
                }
            }
        }
        return builder.toString();
    }

    private void generateMetamodelInterface(Element recordElement) {
        TypeElement typeElement = asTypeElement(recordElement.asType());
        if (!generatedFiles.add(typeElement.getQualifiedName().toString())) {
            return;
        }
        generateMetamodelClass(recordElement);
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaInterfaceName = recordName + "_";
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile(packageName + "." + metaInterfaceName, recordElement);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(String.format("""
                    package %s;

                    import st.orm.template.Metamodel;
                    import javax.annotation.processing.Generated;

                    @Generated("%s")
                    public interface %s extends Metamodel<%s, %s> {
                        %sMetamodel<%s> ROOT = new %sMetamodel<>();
                    
                    %s
                    }""", packageName, getClass().getName(), metaInterfaceName, recordName, recordElement, recordName, recordName, recordName, buildInterfaceFields(recordElement)));
            }
        } catch (Exception e) {
            processingEnv.getMessager()
                    .printMessage(ERROR, "Failed to process " + metaInterfaceName + ". Error: " + e + ".");
        }
    }

    private String buildClassFields(@Nonnull Element recordElement) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponent(enclosed).orElse(null);
            if (recordComponent != null) {
                TypeElement type = asTypeElement(recordComponent);
                if (type != null && isRecord(type)) {
                    if (type.getEnclosingElement().getKind() == CLASS) {
                        continue;   // Skip nested records.
                    }
                    String fieldName = enclosed.getSimpleName().toString();
                    builder.append("    public final ").append(type.getQualifiedName()).append("Metamodel<E> ").append(fieldName).append(";\n");
                } else {
                    String fieldName = enclosed.getSimpleName().toString();
                    String fieldType = getTypeName(enclosed);
                    builder.append("    public final Metamodel<E, ").append(fieldType).append("> ").append(fieldName).append(";\n");
                }
            }
        }
        return builder.toString();
    }

    private String initClassFields(@Nonnull Element recordElement) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponent(enclosed).orElse(null);
            if (recordComponent != null) {
                TypeElement type = asTypeElement(recordComponent);
                if (type != null && isRecord(type)) {
                    if (type.getEnclosingElement().getKind() == CLASS) {
                        continue;   // Skip nested records.
                    }
                    String fieldName = enclosed.getSimpleName().toString();
                    builder.append("        this.").append(fieldName).append(" = new ").append(type.getQualifiedName()).append("Metamodel<>(")
                            .append("path.isEmpty() ? \"").append(fieldName).append("\" : path + \".").append(fieldName).append("\");\n");
                } else {
                    String fieldName = enclosed.getSimpleName().toString();
                    builder.append("        this.").append(fieldName).append(" = () -> path.isEmpty() ? \"").append(fieldName).append("\" : path + \".").append(fieldName).append("\";\n");
                }
            }
        }
        if (!builder.isEmpty()) {
            // Remove trailing newline.
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private void generateMetamodelClass(Element recordElement) {
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaClassName = recordName + "Metamodel";
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile(packageName + "." + metaClassName, recordElement);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(String.format("""
                    package %s;

                    import st.orm.template.Metamodel;
                    import javax.annotation.processing.Generated;

                    @Generated("%s")
                    public final class %s<E extends Record> implements Metamodel<E, %s> {
                        public static final %s<%s> %s = new %s<>();
                    
                    %s
                        private final String path;
                    
                        public %s() {
                            this("");
                        }
                    
                        public %s(String path) {
                            this.path = path;
                    %s
                        }
                    
                        @Override
                        public String path() {
                            return path;
                        }
                    }""", packageName, getClass().getName(), metaClassName, recordName, metaClassName, recordElement,
                        toSnakeCase(recordName), metaClassName, buildClassFields(recordElement), metaClassName,
                        metaClassName, initClassFields(recordElement)));
            }
        } catch (Exception e) {
            processingEnv.getMessager()
                    .printMessage(ERROR, "Failed to process " + metaClassName + ". Error: " + e);
        }
    }
}
