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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * @since 1.2
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
public final class MetamodelProcessor extends AbstractProcessor {

    private static final String METAMODEL_TYPE = "st.orm.template.MetamodelType";
    private static final String GENERATE_METAMODEL = "st.orm.template.GenerateMetamodel";
    private static final String ENTITY = "st.orm.repository.Entity";
    private static final String PROJECTION = "st.orm.repository.Projection";
    private static final String FOREIGN_KEY = "st.orm.FK";

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

    private static boolean isNestedRecord(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return isRecord(element) && element.getEnclosingElement().getKind() == CLASS;
        }
        return false;
    }

    private static boolean isRecord(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return isRecord(element);
        }
        return false;
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

    private static Optional<TypeMirror> getRecordComponentType(Element element) {
        // Using FIELD here instead of RECORD_COMPONENT to support both Java and Kotlin classes.
        if (element.getKind() == FIELD) {
            return ofNullable(element.asType());
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

    private static String getTypeName(TypeMirror typeMirror, String packageName) {
        String className = extractNameIfLazy(typeMirror.toString());
        className = getBoxedTypeName(className);
        if (className.startsWith(packageName)) {
            String simpleName = className.substring(packageName.length() + 1);
            if (!simpleName.contains(".")) {
                return simpleName;
            }
        }
        return className;
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

    private static boolean implementsInterface(TypeMirror typeMirror, String interfaceName, Types types) {
        if (typeMirror == null) {
            return false;
        }
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
            boolean hasGenerateMetamodel = element.getAnnotationMirrors().stream()
                    .anyMatch(annotationMirror -> GENERATE_METAMODEL
                            .equals(annotationMirror.getAnnotationType().toString()));
            // Check if it implements Entity or Projection.
            boolean implementsEntity = implementsInterface(element.asType(), ENTITY, processingEnv.getTypeUtils());
            boolean implementsProjection = implementsInterface(element.asType(), PROJECTION, processingEnv.getTypeUtils());
            // Only generate if it’s annotated OR implements one of those interfaces.
            if (hasGenerateMetamodel || implementsEntity || implementsProjection) {
                generateMetamodelInterface(element);
            }
        }
        return true;
    }

    private Optional<TypeMirror> getMetamodelType(Element element) {
        // Inspect all annotations directly on the element.
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            // Check if the annotation itself is @MetamodelType.
            if (isMetamodelTypeAnnotation(annotationMirror)) {
                TypeMirror annotationValue = getAnnotationValue(annotationMirror);
                if (annotationValue != null) {
                    return Optional.of(elementUtils.getTypeElement(annotationValue.toString()).asType());
                }
            } else {
                // Otherwise, check if the annotation is itself annotated with @MetamodelType.
                Element annotationElement = annotationMirror.getAnnotationType().asElement();
                if (annotationElement instanceof TypeElement typeElement) {
                    for (AnnotationMirror metaAnnotation : typeElement.getAnnotationMirrors()) {
                        if (isMetamodelTypeAnnotation(metaAnnotation)) {
                            TypeMirror annotationValue = getAnnotationValue(metaAnnotation);
                            if (annotationValue != null) {
                                return Optional.of(elementUtils.getTypeElement(annotationValue.toString()).asType());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isMetamodelTypeAnnotation(AnnotationMirror annotationMirror) {
        // Compare the annotation's canonical name to MetamodelType.
        return METAMODEL_TYPE.equals(annotationMirror.getAnnotationType().toString());
    }

    private static TypeMirror getAnnotationValue(AnnotationMirror annotationMirror) {
        // Retrieve the "value" element from the annotation.
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : annotationMirror.getElementValues().entrySet()) {
            if ("value".equals(entry.getKey().getSimpleName().toString())) {
                return (TypeMirror) entry.getValue().getValue();
            }
        }
        return null;
    }

    private TypeMirror getTypeElement(Element recordElement, String fieldName) {
        // Also works for regular records. We may implement a more efficient way in the future for regular records.
        var constructors = recordElement.getEnclosedElements()
                .stream()
                .filter(enclosed -> enclosed.getKind() == CONSTRUCTOR)
                .toList();
        for (var constructor : constructors) {
            var parameters = ((ExecutableElement) constructor).getParameters();
            for (var parameter : parameters) {
                if (parameter.getSimpleName().toString().equals(fieldName)) {
                    TypeMirror type = parameter.asType();
                    return getMetamodelType(parameter).orElse(type);
                }
            }
        }
        return null;
    }

    private boolean isForeignKey(Element recordElement, String fieldName) {
        // If it's a real Java record, attempt to find its canonical constructor.
        if (recordElement.getKind() == RECORD) {
            var canonicalConstructor = findCanonicalConstructor(recordElement);
            if (canonicalConstructor != null) {
                // Check the canonical constructor’s parameters for the foreign key annotation on fieldName
                for (var param : canonicalConstructor.getParameters()) {
                    if (param.getSimpleName().toString().equals(fieldName)) {
                        return hasForeignKeyAnnotation(param);
                    }
                }
                // If we’re here, the fieldName isn’t found in canonical constructor => not a foreign key.
                return false;
            }
        }
        // Fallback for Kotlin “records” or if we couldn’t find a canonical constructor.
        var constructors = recordElement.getEnclosedElements()
                .stream()
                .filter(enclosed -> enclosed.getKind() == CONSTRUCTOR)
                .toList();
        for (var constructor : constructors) {
            var parameters = ((ExecutableElement) constructor).getParameters();
            for (var parameter : parameters) {
                if (parameter.getSimpleName().toString().equals(fieldName)) {
                    return hasForeignKeyAnnotation(parameter);
                }
            }
        }
        return false;
    }

    /**
     * Attempts to find the canonical constructor for a genuine Java record, matching parameter count + types against
     * the record components. Returns null if it can’t find a match (or if not supported).
     */
    private ExecutableElement findCanonicalConstructor(Element recordElement) {
        // Sanity check
        if (recordElement.getKind() != RECORD) {
            return null;
        }
        List<? extends RecordComponentElement> recordComponents =
                ((TypeElement) recordElement).getRecordComponents();
        var constructors = recordElement.getEnclosedElements()
                .stream()
                .filter(enclosed -> enclosed.getKind() == CONSTRUCTOR)
                .map(e -> (ExecutableElement) e)
                .toList();
        // Attempt to match each constructor to the record components in order:
        for (var constructor : constructors) {
            var parameters = constructor.getParameters();
            if (parameters.size() != recordComponents.size()) {
                continue; // Not matching in parameter count.
            }
            boolean matches = true;
            for (int i = 0; i < parameters.size(); i++) {
                var paramType = parameters.get(i).asType().toString();
                var recordType = recordComponents.get(i).asType().toString();
                // Compare type signatures; we may want a better check than string comparison.
                if (!paramType.equals(recordType)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return constructor;
            }
        }
        return null; // No canonical constructor found.
    }

    /**
     * Checks if the given parameter has the FOREIGN_KEY annotation.
     */
    private boolean hasForeignKeyAnnotation(VariableElement parameter) {
        return parameter.getAnnotationMirrors()
                .stream()
                .anyMatch(annotationMirror ->
                        FOREIGN_KEY.equals(annotationMirror.getAnnotationType().toString()));
    }

    private String buildInterfaceFields(Element recordElement, String packageName) {
        StringBuilder builder = new StringBuilder();
        String recordName = recordElement.getSimpleName().toString();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponentType = getRecordComponentType(enclosed).orElse(null);
            if (recordComponentType != null) {
                String fieldName = enclosed.getSimpleName().toString();
                TypeMirror fieldType = getTypeElement(recordElement, fieldName);
                if (fieldType == null) {
                    continue;
                }
                String fieldTypeName = getTypeName(fieldType, packageName);
                if (isRecord(fieldType)) {
                    if (isNestedRecord(fieldType)) {
                        continue;   // Skip nested records.
                    }
                    // Ensure generation of interfaces that may lie in different packages that would not be
                    // picked up by the annotation processor.
                    generateMetamodelInterface(asTypeElement(fieldType));
                    if (isForeignKey(recordElement, fieldName)) {
                        builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName).append("} foreign key. */\n");
                        builder.append("    ").append(fieldTypeName).append("Metamodel<").append(recordName).append("> ").append(fieldName)
                                .append(" = new ").append(fieldTypeName).append("Metamodel<>(\"").append(fieldName).append("\", Metamodel.root(").append(recordName).append(".class));\n");
                    } else {
                        builder.append("    /** Represents the inline {@link ").append(recordName).append("#").append(fieldName).append("} record. */\n");
                        builder.append("    ").append(fieldTypeName).append("Metamodel<").append(recordName).append("> ").append(fieldName)
                                .append(" = new ").append(fieldTypeName).append("Metamodel<>(").append("\"\", \"").append(fieldName).append("\", true, Metamodel.root(").append(recordName).append(".class));\n");
                    }
                } else {
                    builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName).append("} field. */\n");
                    builder.append("    Metamodel<").append(recordName).append(", ").append(fieldTypeName).append("> ").append(fieldName).append(" = new ").append(recordName).append("Metamodel<")
                            .append(recordName).append(">().").append(fieldName).append(";\n");
                }
            }
        }
        if (!builder.isEmpty()) {
            // Remove trailing newline.
            builder.setLength(builder.length() - 1);
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

                    /**
                     * Metamodel for %s.
                     *
                     * @param <T> the record type of the root table of the entity graph.
                     */
                    @Generated("%s")
                    public interface %s extends Metamodel<%s, %s> {
                    %s
                    }""", packageName, recordName, getClass().getName(), metaInterfaceName, recordName, recordName, buildInterfaceFields(recordElement, packageName)));
            }
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(ERROR, "Failed to process " + metaInterfaceName + ". Error: " + e + ".");
        }
    }

    private String buildClassFields(Element recordElement, String packageName, String recordName) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponentType(enclosed).orElse(null);
            if (recordComponent != null) {
                String fieldName = enclosed.getSimpleName().toString();
                TypeMirror fieldType = getTypeElement(recordElement, fieldName);
                if (fieldType == null) {
                    continue;
                }
                String fieldTypeName = getTypeName(fieldType, packageName);
                if (isRecord(fieldType)) {
                    if (isNestedRecord(fieldType)) {
                        continue;   // Skip nested records.
                    }
                    boolean inline = !isForeignKey(recordElement, fieldName);
                    builder.append("    /** Represents the ").append(inline ? "inline " : "").append("{@link ").append(recordName).append("#").append(fieldName).append("} ")
                            .append(inline ? "record." : "foreign key.").append(" */\n");
                    builder.append("    public final ").append(fieldTypeName).append("Metamodel<T> ").append(fieldName).append(";\n");
                } else {
                    builder.append("    /** Represents the ").append("{@link ").append(recordName).append("#").append(fieldName).append("} ")
                            .append("field. */\n");
                    builder.append("    public final Metamodel<T, ").append(fieldTypeName).append("> ").append(fieldName).append(";\n");
                }
            }
        }
        return builder.toString();
    }

    private String initClassFields(Element recordElement, String packageName, String recordName) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponentType(enclosed).orElse(null);
            if (recordComponent != null) {
                String fieldName = enclosed.getSimpleName().toString();
                TypeMirror fieldType = getTypeElement(recordElement, fieldName);
                if (fieldType == null) {
                    continue;
                }
                String fieldTypeName = getTypeName(fieldType, packageName);
                if (isRecord(fieldType)) {
                    if (isNestedRecord(fieldType)) {
                        continue;   // Skip nested records.
                    }
                    if (isForeignKey(recordElement, fieldName)) {
                        builder.append("        this.").append(fieldName).append(" = new ").append(fieldTypeName).append("Metamodel<>(")
                                .append("subPath, componentBase + \"").append(fieldName).append("\", this);\n");
                    } else {
                        builder.append("        this.").append(fieldName).append(" = new ").append(fieldTypeName).append("Metamodel<>(")
                                .append("subPath").append(", componentBase + \"").append(fieldName).append("\", true, this);\n");
                    }
                } else {
                    builder.append("        this.").append(fieldName).append(" = new MetamodelImpl(")
                            .append(fieldTypeName).append(".class, ").append("subPath").append(", componentBase + \"").append(fieldName).append("\", false, this);\n");
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
                    import st.orm.template.impl.MetamodelImpl;
                    import javax.annotation.processing.Generated;

                    /**
                     * Metamodel implementation for %s.
                     *
                     * @param <T> the record type of the root table of the entity graph.
                     */
                    @Generated("%s")
                    public final class %s<T extends Record> extends MetamodelImpl<T, %s> {
                    %s
                        public %s() {
                            this("", (Metamodel<T, ?>) Metamodel.root(%s.class));
                        }
                    
                        public %s(String component, Metamodel<T, ?> parent) {
                            this("", component, parent);
                        }
                    
                        public %s(String path, String component, Metamodel<T, ?> parent) {
                            this(path, component, false, parent);
                        }
                    
                        public %s(String path, String component, boolean inline, Metamodel<T, ?> parent) {
                            super(%s.class, path, component, inline, parent);
                            String subPath = inline ? path : component.isEmpty() ? path : path.isEmpty() ? component : path + "." + component;
                            String componentBase = inline ? component.isEmpty() ? "" : component + "." : "";
                    %s
                        }
                    }""", packageName, recordName, getClass().getName(), metaClassName, recordName, buildClassFields(recordElement, packageName, recordName),
                        metaClassName, recordName, metaClassName, metaClassName, metaClassName, recordName, initClassFields(recordElement, packageName, recordName)));
            }
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(ERROR, "Failed to process " + metaClassName + ". Error: " + e);
        }
    }
}
