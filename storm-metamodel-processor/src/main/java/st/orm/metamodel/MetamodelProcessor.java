/*
 * Copyright 2024 - 2026 the original author or authors.
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

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * @since 1.2
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
public final class MetamodelProcessor extends AbstractProcessor {

    private static final String METAMODEL_TYPE = "st.orm.MetamodelType";
    private static final String GENERATE_METAMODEL = "st.orm.GenerateMetamodel";
    private static final String DATA = "st.orm.Data";
    private static final String FOREIGN_KEY = "st.orm.FK";
    private static final String PRIMARY_KEY = "st.orm.PK";
    private static final String UNIQUE_KEY = "st.orm.UK";

    private static final Pattern REF_PATTERN = Pattern.compile("^st\\.orm\\.Ref<([^>]+)>$");

    /**
     * Tracks which record types we already generated a metamodel class for.
     */
    private final Set<String> generatedMetamodelClasses;

    /**
     * Tracks which record types we already generated a metamodel interface for.
     */
    private final Set<String> generatedMetamodelInterfaces;

    /**
     * Tracks which record types we already expanded (walked referenced record fields) for.
     * Prevents infinite recursion on cyclic graphs (StackOverflowError).
     */
    private final Set<String> expandedReferencedRecords;

    /**
     * Tracks which record types we already checked for non-Ref record cycles.
     */
    private final Set<String> checkedForRecordCycles;

    /**
     * Qualified names of the record types on the current cycle-check walk, in walk order, so a detected cycle is
     * reported by naming its members.
     */
    private final Set<String> recordCyclePath;

    /**
     * Tracks which record types we already generated a reference metamodel class ({@code <Type>RefMetamodel}) for.
     */
    private final Set<String> generatedReferenceMetamodels;

    /**
     * Tracks which record types we already generated a navigation-only metamodel class
     * ({@code Navigable<Type>Metamodel}) for. Prevents infinite recursion on cyclic reference graphs.
     */
    private final Set<String> generatedNavigableMetamodels;

    /**
     * Qualified names of the types currently being expanded into navigation-only metamodels. A navigation child whose
     * target type is already on this path forms a cycle (for example a self-referencing {@code Ref}); it is emitted as
     * a navigation leaf so eager construction of the generated metamodels terminates.
     */
    private final Set<String> navPath;

    /**
     * Fully qualified names of the generated instantiators, registered as services when processing is over.
     */
    private final Set<String> generatedInstantiators;

    private Elements elementUtils;
    private Types typeUtils;

    public MetamodelProcessor() {
        this.generatedMetamodelClasses = new HashSet<>();
        this.generatedMetamodelInterfaces = new HashSet<>();
        this.expandedReferencedRecords = new HashSet<>();
        this.checkedForRecordCycles = new HashSet<>();
        this.recordCyclePath = new LinkedHashSet<>();
        this.generatedReferenceMetamodels = new HashSet<>();
        this.generatedNavigableMetamodels = new HashSet<>();
        this.navPath = new HashSet<>();
        this.generatedInstantiators = new LinkedHashSet<>();
    }

    @Override
    public synchronized void init(@Nonnull ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
    }

    private static boolean isNestedRecord(@Nonnull TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return isRecord(element) && element.getEnclosingElement().getKind() == CLASS;
        }
        return false;
    }

    private static boolean isRecord(@Nonnull TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return isRecord(element);
        }
        return false;
    }

    private static boolean isRecord(@Nonnull Element element) {
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

    private static Optional<TypeMirror> getRecordComponentType(@Nonnull Element element) {
        // Using FIELD here instead of RECORD_COMPONENT to support both Java and Kotlin classes.
        if (element.getKind() == FIELD) {
            return ofNullable(element.asType());
        }
        return empty();
    }

    private static String extractNameIfRef(@Nonnull String input) {
        Matcher matcher = REF_PATTERN.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return input;
    }

    private static boolean isRefType(@Nonnull TypeMirror typeMirror) {
        String s = typeMirror.toString();
        return s.startsWith("st.orm.Ref<") && s.endsWith(">");
    }

    /**
     * Unwraps Ref<T> to T (logical field type).
     */
    private static String getTypeName(@Nonnull TypeMirror typeMirror, @Nonnull String packageName) {
        String className = extractNameIfRef(typeMirror.toString());
        className = className.replaceAll("@\\S+\\s+", "");  // Erase any annotations.
        className = getBoxedTypeName(className);
        if (!packageName.isEmpty() && className.startsWith(packageName + ".")) {
            String simpleName = className.substring(packageName.length() + 1);
            if (!simpleName.contains(".")) {
                return simpleName;
            }
        }
        return className;
    }

    /**
     * V does NOT unwrap Ref<T>, because getValue() returns Ref<T> for ref fields.
     */
    private static String getValueTypeName(@Nonnull TypeMirror typeMirror, @Nonnull String packageName) {
        String className = typeMirror.toString();
        className = className.replaceAll("@\\S+\\s+", ""); // erase annotations
        // If a primitive, box it.
        className = getBoxedTypeName(className);
        if (className.startsWith("st.orm.Ref<") && className.endsWith(">")) {
            String inner = className.substring("st.orm.Ref<".length(), className.length() - 1).trim();
            inner = inner.replaceAll("@\\S+\\s+", "");
            inner = getBoxedTypeName(inner);
            if (!packageName.isEmpty() && inner.startsWith(packageName + ".")) {
                String simpleInner = inner.substring(packageName.length() + 1);
                if (!simpleInner.contains(".")) inner = simpleInner;
            }
            return "st.orm.Ref<" + inner + ">";
        }
        if (!packageName.isEmpty() && className.startsWith(packageName + ".")) {
            String simple = className.substring(packageName.length() + 1);
            if (!simple.contains(".")) return simple;
        }
        return className;
    }

    @Nullable
    private static TypeElement asTypeElement(@Nullable TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            return (element instanceof TypeElement) ? (TypeElement) element : null;
        }
        return null;
    }

    private static String getBoxedTypeName(@Nonnull String name) {
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

    private static boolean implementsInterface(@Nullable TypeMirror typeMirror,
                                               @Nonnull String interfaceName,
                                               @Nonnull Types types) {
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
            return implementsInterface(typeElement.getSuperclass(), interfaceName, types);
        }
        return false;
    }

    private boolean implementsData(@Nonnull Element recordElement) {
        return implementsInterface(recordElement.asType(), DATA, typeUtils);
    }

    @Override
    public boolean process(@Nonnull Set<? extends TypeElement> annotations,
                           @Nonnull RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(NOTE, "Storm Metamodel Processor is running.");
        for (Element element : roundEnv.getRootElements()) {
            try {
                if (isRecord(element)) {
                    boolean hasGenerateMetamodel = element.getAnnotationMirrors().stream()
                            .anyMatch(annotationMirror -> GENERATE_METAMODEL
                                    .equals(annotationMirror.getAnnotationType().toString()));

                    boolean isData = implementsData(element);
                    if (hasGenerateMetamodel || isData) {
                        generateMetamodelArtifacts(element);
                    }
                } else if (element.getKind() == ElementKind.INTERFACE
                        && element instanceof TypeElement typeElement
                        && typeElement.getModifiers().contains(javax.lang.model.element.Modifier.SEALED)
                        && implementsData(element)) {
                    List<ExecutableElement> declaredGetters = getDeclaredAbstractGetters(typeElement);
                    if (!declaredGetters.isEmpty()) {
                        generateSealedMetamodelArtifacts(typeElement, declaredGetters);
                    }
                }
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(ERROR,
                        "Failed to process metamodel for " + element + ": " + e + "\n" + stackTraceOf(e),
                        element);
                throw (e instanceof RuntimeException runtimeException)
                        ? runtimeException
                        : new IllegalStateException(e);
            }
        }
        if (roundEnv.processingOver()) {
            writeInstantiatorServices();
        }
        return false;
    }

    private static String stackTraceOf(@Nonnull Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    /**
     * Registers the generated instantiators in {@code META-INF/services/st.orm.mapping.Instantiator}, allowing the
     * runtime to discover them through the {@code ServiceLoader} and construct records without reflection.
     */
    private void writeInstantiatorServices() {
        if (generatedInstantiators.isEmpty()) {
            return;
        }
        try {
            FileObject fileObject = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/st.orm.mapping.Instantiator");
            try (Writer writer = fileObject.openWriter()) {
                for (String instantiator : generatedInstantiators) {
                    writer.write(instantiator);
                    writer.write("\n");
                }
            }
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(ERROR,
                    "Failed to write instantiator services file: " + e + "\n" + stackTraceOf(e));
        }
    }

    /**
     * Walk record-typed fields on this record and ensure metamodels exist for referenced record types.
     *
     * This fixes the “only recursing from the _ interface” problem, where deeper nested records (like
     * CampaignDetails -> AcquisitionDetails) would not be generated if the intermediate record does not
     * implement Data (so no "_" interface is generated for it).
     */
    private void generateReferencedRecordMetamodels(@Nonnull Element recordElement) {
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();

        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponentType = getRecordComponentType(enclosed).orElse(null);
            if (recordComponentType == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            // Follow direct record references (not nested record definitions).
            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;

                TypeElement nestedTypeEl = asTypeElement(fieldType);
                if (nestedTypeEl != null) {
                    generateMetamodelArtifacts(nestedTypeEl);
                }
            } else if (isRefType(fieldType)) {
                // A Ref<X> field needs a reference metamodel for X and navigation-only metamodels beyond it.
                TypeElement refTargetEl = asTypeElement(unwrapRefType(fieldType));
                if (refTargetEl != null) {
                    generateReferenceArtifacts(refTargetEl);
                }
            }
        }
    }

    /**
     * Rejects cycles in the graph of non-Ref record fields. The generated metamodels construct their record-typed
     * children eagerly, so a cycle that does not cross a {@code Ref} boundary fails at class initialization. The
     * engine states the same rule at template level for self-references: a foreign key cycle must be marked as
     * {@code Ref} to be loadable.
     */
    private void checkRecordCycles(@Nonnull TypeElement recordElement) {
        String qualifiedName = recordElement.getQualifiedName().toString();
        if (!checkedForRecordCycles.add(qualifiedName)) return;
        recordCyclePath.add(qualifiedName);
        try {
            for (Element enclosed : recordElement.getEnclosedElements()) {
                if (getRecordComponentType(enclosed).isEmpty()) continue;
                String fieldName = enclosed.getSimpleName().toString();
                TypeMirror fieldType = getTypeElement(recordElement, fieldName);
                if (fieldType == null) continue;
                if (!isRecord(fieldType) || isRefType(fieldType) || isNestedRecord(fieldType)) continue;
                TypeElement child = asTypeElement(fieldType);
                if (child == null) continue;
                String childQualifiedName = child.getQualifiedName().toString();
                if (recordCyclePath.contains(childQualifiedName)) {
                    String cycle = renderCycle(childQualifiedName);
                    if (implementsData(child)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "Cycle of non-Ref foreign keys: " + cycle + ". "
                                + "A foreign key cycle must cross a Ref boundary to be loadable. "
                                + "Mark one of the foreign keys as Ref (for example Ref<" + child.getSimpleName()
                                + ">) to break the cycle.",
                                enclosed);
                    } else {
                        processingEnv.getMessager().printMessage(ERROR,
                                "Cycle of inline records: " + cycle + ". "
                                + "An inline record embeds its fields in the enclosing table, so a cycle cannot "
                                + "be modeled.",
                                enclosed);
                    }
                } else {
                    checkRecordCycles(child);
                }
            }
        } finally {
            recordCyclePath.remove(qualifiedName);
        }
    }

    /**
     * Renders the members of the detected cycle: the tail of the current walk from the type the cycle re-enters,
     * closed by naming that type again.
     */
    private String renderCycle(@Nonnull String cycleStart) {
        StringBuilder cycle = new StringBuilder();
        boolean inCycle = false;
        for (String qualifiedName : recordCyclePath) {
            inCycle = inCycle || qualifiedName.equals(cycleStart);
            if (inCycle) {
                cycle.append(simpleNameOf(qualifiedName)).append(" -> ");
            }
        }
        return cycle.append(simpleNameOf(cycleStart)).toString();
    }

    private static String simpleNameOf(@Nonnull String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    /**
     * Generates the metamodel class for all records.
     * Generates the metamodel interface only if the record implements Data (directly or indirectly).
     */
    private void generateMetamodelArtifacts(@Nonnull Element recordElement) {
        TypeElement typeElement = asTypeElement(recordElement.asType());
        if (typeElement == null) return;
        checkRecordCycles(typeElement);

        String qn = typeElement.getQualifiedName().toString();
        boolean isData = implementsData(recordElement);

        // Always generate both chain variants once; a nullable field selects the nullable variant of its child.
        if (generatedMetamodelClasses.add(qn)) {
            generateMetamodelClass(recordElement, false);
            generateMetamodelClass(recordElement, true);
            generateInstantiator(recordElement);
        }

        // Only generate the interface for Data records.
        if (isData && generatedMetamodelInterfaces.add(qn)) {
            generateMetamodelInterface(recordElement);
        }

        // Recurse into referenced record types only once per record type to avoid cycles.
        if (expandedReferencedRecords.add(qn)) {
            generateReferencedRecordMetamodels(recordElement);
        }
    }

    private Optional<TypeMirror> getMetamodelType(@Nonnull Element element) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (isMetamodelTypeAnnotation(annotationMirror)) {
                TypeMirror annotationValue = getAnnotationValue(annotationMirror);
                if (annotationValue != null) {
                    return Optional.of(elementUtils.getTypeElement(annotationValue.toString()).asType());
                }
            } else {
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

    private static boolean isMetamodelTypeAnnotation(@Nonnull AnnotationMirror annotationMirror) {
        return METAMODEL_TYPE.equals(annotationMirror.getAnnotationType().toString());
    }

    @Nullable
    private static TypeMirror getAnnotationValue(@Nonnull AnnotationMirror annotationMirror) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : annotationMirror.getElementValues().entrySet()) {
            if ("value".equals(entry.getKey().getSimpleName().toString())) {
                return (TypeMirror) entry.getValue().getValue();
            }
        }
        return null;
    }

    /**
     * Returns the type the field is addressed as in the metamodel: the type {@link st.orm.MetamodelType} names when
     * the field carries it, the declared type otherwise.
     *
     * <p>A converted field, a {@code @Json} column in particular, is stored as one type and held as another. The
     * addressed type is what a predicate compares against, so it is the type the metamodel's {@code E} parameter
     * carries. The value the record holds keeps its declared type; see
     * {@link #getDeclaredTypeElement(Element, String)}.</p>
     */
    @Nullable
    private TypeMirror getTypeElement(@Nonnull Element recordElement, @Nonnull String fieldName) {
        return getTypeElement(recordElement, fieldName, true);
    }

    /**
     * Returns the field's declared type, ignoring any {@link st.orm.MetamodelType} override.
     *
     * <p>This is the type the record accessor returns, so it is the type the metamodel's {@code V} parameter carries
     * and the type {@code getValue} is generated against.</p>
     */
    @Nullable
    private TypeMirror getDeclaredTypeElement(@Nonnull Element recordElement, @Nonnull String fieldName) {
        return getTypeElement(recordElement, fieldName, false);
    }

    @Nullable
    private TypeMirror getTypeElement(@Nonnull Element recordElement, @Nonnull String fieldName,
                                      boolean applyMetamodelType) {
        // The canonical constructor defines the component types. A convenience constructor may reuse a component name
        // for a parameter of another type — a record holding a Ref<City> taking the City it refers to, say — so
        // scanning every constructor would resolve the field to whichever one is enclosed first.
        var canonicalConstructor = findCanonicalConstructor(recordElement);
        var constructors = canonicalConstructor != null
                ? List.<Element>of(canonicalConstructor)
                : recordElement.getEnclosedElements()
                        .stream()
                        .filter(enclosed -> enclosed.getKind() == CONSTRUCTOR)
                        .toList();
        for (var constructor : constructors) {
            var parameters = ((ExecutableElement) constructor).getParameters();
            for (var parameter : parameters) {
                if (parameter.getSimpleName().toString().equals(fieldName)) {
                    TypeMirror type = parameter.asType();
                    return applyMetamodelType ? getMetamodelType(parameter).orElse(type) : type;
                }
            }
        }
        return null;
    }

    private boolean isDataType(@Nonnull Element recordElement, @Nonnull String fieldName) {
        if (recordElement.getKind() == RECORD) {
            var canonicalConstructor = findCanonicalConstructor(recordElement);
            if (canonicalConstructor != null) {
                for (var param : canonicalConstructor.getParameters()) {
                    if (param.getSimpleName().toString().equals(fieldName)) {
                        return isDataType(param);
                    }
                }
                return false;
            }
        }
        var constructors = recordElement.getEnclosedElements()
                .stream()
                .filter(enclosed -> enclosed.getKind() == CONSTRUCTOR)
                .toList();
        for (var constructor : constructors) {
            var parameters = ((ExecutableElement) constructor).getParameters();
            for (var parameter : parameters) {
                if (parameter.getSimpleName().toString().equals(fieldName)) {
                    return isDataType(parameter);
                }
            }
        }
        return false;
    }

    private boolean isDataType(@Nonnull VariableElement parameter) {
        // Only declared types can be "extends/implements Data"
        var mirror = parameter.asType();
        if (!(mirror instanceof DeclaredType declared)) {
            return false;
        }

        var dataElement = processingEnv.getElementUtils().getTypeElement(DATA);
        if (dataElement == null) {
            // Data type not on the classpath
            return false;
        }

        var dataType = dataElement.asType();
        return processingEnv.getTypeUtils().isAssignable(declared, dataType);
    }

    @Nullable
    private ExecutableElement findCanonicalConstructor(@Nonnull Element recordElement) {
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

        for (var constructor : constructors) {
            var parameters = constructor.getParameters();
            if (parameters.size() != recordComponents.size()) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < parameters.size(); i++) {
                var paramType = parameters.get(i).asType().toString();
                var recordType = recordComponents.get(i).asType().toString();
                if (!paramType.equals(recordType)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return constructor;
            }
        }
        return null;
    }

    private static boolean hasAnnotationOrMeta(@Nonnull Element element, @Nonnull String annotationFqn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            // Direct
            if (annotationFqn.equals(am.getAnnotationType().toString())) {
                return true;
            }
            // Meta-annotation
            Element annEl = am.getAnnotationType().asElement();
            if (annEl instanceof TypeElement te) {
                for (AnnotationMirror meta : te.getAnnotationMirrors()) {
                    if (annotationFqn.equals(meta.getAnnotationType().toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the field with the given name on the record element has a {@code @Unique}
     * annotation (directly or as a meta-annotation, e.g. via {@code @PK}).
     */
    private static boolean isUniqueField(@Nonnull Element recordElement, @Nonnull String fieldName) {
        // Check record components (Java records).
        if (recordElement.getKind() == RECORD && recordElement instanceof TypeElement te) {
            for (RecordComponentElement rc : te.getRecordComponents()) {
                if (rc.getSimpleName().toString().equals(fieldName)) {
                    return hasAnnotationOrMeta(rc, UNIQUE_KEY);
                }
            }
        }
        // Check constructor parameters (works for both Java and Kotlin).
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != CONSTRUCTOR) continue;
            for (VariableElement param : ((ExecutableElement) enclosed).getParameters()) {
                if (param.getSimpleName().toString().equals(fieldName)) {
                    return hasAnnotationOrMeta(param, UNIQUE_KEY);
                }
            }
        }
        return false;
    }

    /** Nullable markers, matching the runtime contract (JetBrains covers Kotlin kapt stubs). */
    private static final Set<String> NULLABLE_ANNOTATIONS = Set.of(
            "org.jspecify.annotations.Nullable",
            "jakarta.annotation.Nullable",
            "javax.annotation.Nullable",
            "org.jetbrains.annotations.Nullable");

    /** Non-null markers, matching the runtime contract (JetBrains covers Kotlin kapt stubs). */
    private static final Set<String> NONNULL_ANNOTATIONS = Set.of(
            "org.jspecify.annotations.NonNull",
            "jakarta.annotation.Nonnull",
            "javax.annotation.Nonnull",
            "org.jetbrains.annotations.NotNull");

    private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
    private static final String NULL_UNMARKED = "org.jspecify.annotations.NullUnmarked";

    /**
     * Returns whether the element carries any of the given annotations, checking both the declaration and the
     * type use (JSpecify annotations annotate the type rather than the declaration).
     */
    private static boolean hasAnyAnnotation(@Nonnull Element element, @Nonnull Set<String> names) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (names.contains(am.getAnnotationType().toString())) {
                return true;
            }
        }
        for (AnnotationMirror am : element.asType().getAnnotationMirrors()) {
            if (names.contains(am.getAnnotationType().toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the nearest {@code @NullUnmarked} / {@code @NullMarked} marker by walking the enclosing elements
     * (class, enclosing classes, package, module). Without a marker the scope is null-marked: this mirrors the
     * runtime contract, where models are null-marked by default.
     */
    private static boolean isNullUnmarkedScope(@Nonnull Element element) {
        for (Element enclosing = element; enclosing != null; enclosing = enclosing.getEnclosingElement()) {
            for (AnnotationMirror am : enclosing.getAnnotationMirrors()) {
                String name = am.getAnnotationType().toString();
                if (NULL_UNMARKED.equals(name)) {
                    return true;
                }
                if (NULL_MARKED.equals(name)) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isPrimaryKeyField(@Nonnull Element recordElement, @Nonnull String fieldName) {
        if (recordElement.getKind() == RECORD && recordElement instanceof TypeElement te) {
            for (RecordComponentElement rc : te.getRecordComponents()) {
                if (rc.getSimpleName().toString().equals(fieldName)) {
                    return hasAnnotationOrMeta(rc, PRIMARY_KEY);
                }
            }
        }
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != CONSTRUCTOR) continue;
            for (VariableElement param : ((ExecutableElement) enclosed).getParameters()) {
                if (param.getSimpleName().toString().equals(fieldName)) {
                    return hasAnnotationOrMeta(param, PRIMARY_KEY);
                }
            }
        }
        return false;
    }

    private boolean isNullableUniqueField(@Nonnull Element recordElement, @Nonnull String fieldName) {
        // PK fields are always non-null.
        if (isPrimaryKeyField(recordElement, fieldName)) return false;
        return isNullableField(recordElement, fieldName);
    }

    /**
     * Returns the derived nullability of a field, matching the runtime contract: primitives are never null,
     * explicit annotations win (nullable before non-null), and unannotated fields are non-null unless a
     * {@code @NullUnmarked} scope applies.
     */
    private boolean isNullableField(@Nonnull Element recordElement, @Nonnull String fieldName) {
        // Primitive types are never null.
        TypeMirror fieldType = getTypeElement(recordElement, fieldName);
        if (fieldType != null && isPrimitiveReturn(fieldType)) return false;

        // Check record components first.
        if (recordElement.getKind() == RECORD && recordElement instanceof TypeElement te) {
            for (RecordComponentElement rc : te.getRecordComponents()) {
                if (rc.getSimpleName().toString().equals(fieldName)) {
                    if (hasAnyAnnotation(rc, NULLABLE_ANNOTATIONS)) return true;
                    if (hasAnyAnnotation(rc, NONNULL_ANNOTATIONS)) return false;
                }
            }
        }
        // Check constructor parameters (works for both Java and Kotlin).
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != CONSTRUCTOR) continue;
            for (VariableElement param : ((ExecutableElement) enclosed).getParameters()) {
                if (param.getSimpleName().toString().equals(fieldName)) {
                    if (hasAnyAnnotation(param, NULLABLE_ANNOTATIONS)) return true;
                    if (hasAnyAnnotation(param, NONNULL_ANNOTATIONS)) return false;
                }
            }
        }
        // Models are null-marked by default; only a @NullUnmarked scope makes unannotated fields nullable.
        return isNullUnmarkedScope(recordElement);
    }

    /**
     * Returns {@code true} if the given record element has at least one leaf field that is nullable.
     * Recursively walks inline sub-records. Used to determine if a compound key has nullable constituents.
     */
    private boolean hasNullableLeaf(@Nullable TypeElement recordElement) {
        if (recordElement == null) return false;
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponentType = getRecordComponentType(enclosed).orElse(null);
            if (recordComponentType == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            if (isRecord(fieldType) && !isRefType(fieldType) && !isNestedRecord(fieldType)) {
                if (!isDataType(recordElement, fieldName)) {
                    // Inline sub-record: recurse into it.
                    if (hasNullableLeaf(asTypeElement(fieldType))) return true;
                }
                // FK (Data) fields: not part of compound key leaves; skip.
            } else {
                // Scalar leaf: nullable if not primitive, not @PK, not @Nonnull.
                if (isNullableUniqueField(recordElement, fieldName)) return true;
            }
        }
        return false;
    }

    private static boolean extractNullsDistinct(@Nonnull Element element) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            String annotationName = am.getAnnotationType().toString();
            if (UNIQUE_KEY.equals(annotationName)) {
                // Direct @UK annotation.
                for (var entry : am.getElementValues().entrySet()) {
                    if ("nullsDistinct".equals(entry.getKey().getSimpleName().toString())) {
                        return (Boolean) entry.getValue().getValue();
                    }
                }
                return true; // default value
            }
            // Check meta-annotation (e.g. @PK which has @UK).
            Element annEl = am.getAnnotationType().asElement();
            if (annEl instanceof TypeElement te) {
                for (AnnotationMirror meta : te.getAnnotationMirrors()) {
                    if (UNIQUE_KEY.equals(meta.getAnnotationType().toString())) {
                        // Meta-annotated @UK does not carry the user's nullsDistinct attribute.
                        return true;
                    }
                }
            }
        }
        return true; // default
    }

    private static boolean getNullsDistinct(@Nonnull Element recordElement, @Nonnull String fieldName) {
        // Check record components.
        if (recordElement.getKind() == RECORD && recordElement instanceof TypeElement te) {
            for (RecordComponentElement rc : te.getRecordComponents()) {
                if (rc.getSimpleName().toString().equals(fieldName)) {
                    return extractNullsDistinct(rc);
                }
            }
        }
        // Check constructor parameters.
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != CONSTRUCTOR) continue;
            for (VariableElement param : ((ExecutableElement) enclosed).getParameters()) {
                if (param.getSimpleName().toString().equals(fieldName)) {
                    return extractNullsDistinct(param);
                }
            }
        }
        return true; // default
    }

    private Optional<String> findPrimaryKeyFieldName(@Nonnull Element recordElement) {
        // Java record components.
        if (recordElement.getKind() == RECORD && recordElement instanceof TypeElement te) {
            for (RecordComponentElement rc : te.getRecordComponents()) {
                if (hasAnnotationOrMeta(rc, PRIMARY_KEY)) {
                    return Optional.of(rc.getSimpleName().toString());
                }
            }
        }
        // Constructor parameters (works well for Kotlin stubs too).
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != CONSTRUCTOR) continue;
            ExecutableElement ctor = (ExecutableElement) enclosed;
            for (VariableElement p : ctor.getParameters()) {
                if (hasAnnotationOrMeta(p, PRIMARY_KEY)) {
                    return Optional.of(p.getSimpleName().toString());
                }
            }
        }
        // Fields (fallback).
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() == FIELD && hasAnnotationOrMeta(enclosed, PRIMARY_KEY)) {
                return Optional.of(enclosed.getSimpleName().toString());
            }
        }
        return Optional.empty();
    }

    private static String capitalize(@Nonnull String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isBooleanType(@Nonnull TypeMirror t) {
        String n = t.toString();
        return "boolean".equals(n) || "java.lang.Boolean".equals(n);
    }

    /**
     * Returns Java source accessor expression for a property on an instance expression.
     * - Java record: expr.field().
     * - Kotlin/JavaBean: expr.getField() / expr.isField() for boolean.
     */
    private static String accessorExpr(@Nonnull Element ownerType,
                                       @Nonnull String instanceExpr,
                                       @Nonnull String fieldName,
                                       @Nonnull TypeMirror fieldType) {
        if (ownerType.getKind() == RECORD) {
            return instanceExpr + "." + fieldName + "()";
        }
        String suffix = capitalize(fieldName);
        if (isBooleanType(fieldType)) {
            return instanceExpr + ".is" + suffix + "()";
        }
        return instanceExpr + ".get" + suffix + "()";
    }

    private static boolean isPrimitiveReturn(@Nonnull TypeMirror t) {
        return switch (t.toString()) {
            case "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
            default -> false;
        };
    }

    private static boolean isFloat(@Nonnull TypeMirror t) {
        return "float".equals(t.toString());
    }

    private static boolean isDouble(@Nonnull TypeMirror t) {
        return "double".equals(t.toString());
    }

    private static String sameComparisonExpr(@Nonnull String left, @Nonnull String right, @Nonnull TypeMirror t) {
        if (isFloat(t)) {
            return "Float.floatToIntBits(" + left + ") == Float.floatToIntBits(" + right + ")";
        }
        if (isDouble(t)) {
            return "Double.doubleToLongBits(" + left + ") == Double.doubleToLongBits(" + right + ")";
        }
        if (isPrimitiveReturn(t)) {
            return left + " == " + right;
        }
        // Java arrays don't override equals — Objects.equals would do reference comparison.
        // Arrays.equals handles both null operands and shallow content equality for primitive
        // and Object arrays.
        if (t.getKind() == TypeKind.ARRAY) {
            return "java.util.Arrays.equals(" + left + ", " + right + ")";
        }
        return "Objects.equals(" + left + ", " + right + ")";
    }

    private static String identicalComparisonExpr(@Nonnull String left, @Nonnull String right, @Nonnull TypeMirror t) {
        if (isFloat(t)) {
            return "Float.floatToIntBits(" + left + ") == Float.floatToIntBits(" + right + ")";
        }
        if (isDouble(t)) {
            return "Double.doubleToLongBits(" + left + ") == Double.doubleToLongBits(" + right + ")";
        }
        if (isPrimitiveReturn(t)) {
            return left + " == " + right;
        }
        return left + " == " + right;
    }

    private String buildInterfaceFields(@Nonnull Element recordElement, @Nonnull String packageName) {
        StringBuilder builder = new StringBuilder();
        String recordName = recordElement.getSimpleName().toString();
        String modelRef = recordName + "Metamodel.<" + recordName + ">instance()";
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponentType = getRecordComponentType(enclosed).orElse(null);
            if (recordComponentType == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            String fieldTypeName = getTypeName(fieldType, packageName);

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                // Always generate the nested metamodel class; only generate its interface if it is Data.
                TypeElement nestedTypeEl = asTypeElement(fieldType);
                if (nestedTypeEl != null) {
                    generateMetamodelArtifacts(nestedTypeEl);
                }
                boolean inline = !isDataType(recordElement, fieldName);
                String childMetamodel = metamodelClassName(fieldTypeName, isNullableField(recordElement, fieldName));
                builder.append("    /** Represents the ")
                        .append(inline ? "inline " : "")
                        .append("{@link ").append(recordName).append("#").append(fieldName).append("} ")
                        .append(inline ? "record." : "foreign key.")
                        .append(" */\n");
                builder.append("    ").append(childMetamodel).append("<").append(recordName).append("> ")
                        .append(fieldName).append(" = ").append(modelRef).append(".")
                        .append(fieldName).append(";\n");
            } else if (isRefType(fieldType)) {
                builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName)
                        .append("} reference. */\n");
                builder.append("    ").append(refClassName(fieldTypeName))
                        .append("<").append(recordName).append("> ")
                        .append(fieldName).append(" = ").append(modelRef).append(".")
                        .append(fieldName).append(";\n");
            } else {
                String valueTypeName = getValueTypeName(getDeclaredTypeElement(recordElement, fieldName), packageName);
                boolean unique = isEffectivelyUniqueField(recordElement, fieldName);
                String baseClass = unique ? "AbstractKeyMetamodel" : "AbstractMetamodel";

                builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName)
                        .append("} field. */\n");
                builder.append("    ").append(baseClass).append("<").append(recordName).append(", ").append(fieldTypeName)
                        .append(", ").append(valueTypeName).append("> ")
                        .append(fieldName).append(" = ").append(modelRef).append(".")
                        .append(fieldName).append(";\n");
            }
        }
        if (!builder.isEmpty()) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    /**
     * Generates an {@code Instantiator} implementation that invokes the record's canonical constructor directly,
     * allowing the runtime to construct instances without reflection (no reflection configuration for native
     * images, no {@code opens} clauses for modular applications).
     *
     * <p>Only top-level, non-generic Java records are supported; other types fall back to reflective
     * construction at runtime.</p>
     */
    private void generateInstantiator(@Nonnull Element recordElement) {
        if (recordElement.getKind() != RECORD) {
            return; // Kotlin classes are handled by the KSP processor.
        }
        TypeElement typeElement = asTypeElement(recordElement.asType());
        if (typeElement == null
                || typeElement.getEnclosingElement().getKind() != ElementKind.PACKAGE
                || !typeElement.getTypeParameters().isEmpty()) {
            return;
        }
        ExecutableElement constructor = findCanonicalConstructor(recordElement);
        if (constructor == null) {
            return;
        }
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String instantiatorName = recordName + "Instantiator";
        var parameters = constructor.getParameters();
        StringBuilder arguments = new StringBuilder();
        StringBuilder components = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            String castType = getBoxedTypeName(parameters.get(i).asType().toString().replaceAll("@\\S+\\s+", ""));
            arguments.append(arguments.isEmpty() ? "" : ",\n")
                    .append("                (").append(castType).append(") args[").append(i).append("]");
            components.append(components.isEmpty() ? "" : ",\n")
                    .append("                instance.").append(parameters.get(i).getSimpleName()).append("()");
        }
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + instantiatorName, recordElement);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(String.format("""
                    %simport javax.annotation.processing.Generated;

                    /**
                     * Instantiator for %s; constructs and deconstructs instances without reflection.
                     */
                    @Generated("%s")
                    public final class %s implements st.orm.mapping.Instantiator<%s> {

                        @Override
                        public Class<%s> type() {
                            return %s.class;
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public %s instantiate(Object[] args) {
                            return new %s(
                    %s
                            );
                        }

                        @Override
                        public Object[] deconstruct(%s instance) {
                            return new Object[] {
                    %s
                            };
                        }
                    }""",
                        (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n"),
                        recordName,
                        getClass().getName(),
                        instantiatorName,
                        recordName,
                        recordName,
                        recordName,
                        recordName,
                        recordName,
                        arguments,
                        recordName,
                        components
                ));
            }
            generatedInstantiators.add((packageName.isEmpty() ? "" : packageName + ".") + instantiatorName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + instantiatorName, e);
        }
    }

    private void generateMetamodelInterface(@Nonnull Element recordElement) {
        TypeElement typeElement = asTypeElement(recordElement.asType());
        if (typeElement == null) return;

        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaInterfaceName = recordName + "_";
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + metaInterfaceName, recordElement);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(String.format("""
                    %simport st.orm.Metamodel;
                    import st.orm.AbstractMetamodel;
                    import st.orm.AbstractKeyMetamodel;
                    import javax.annotation.processing.Generated;

                    /**
                     * Metamodel for %s.
                     */
                    @Generated("%s")
                    public interface %s extends Metamodel<%s, %s> {
                    %s
                    }""",
                        (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n"),
                        recordName,
                        getClass().getName(),
                        metaInterfaceName,
                        recordName,
                        recordName,
                        buildInterfaceFields(recordElement, packageName)
                ));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + metaInterfaceName, e);
        }
    }

    private String buildClassFields(@Nonnull Element recordElement,
                                    @Nonnull String packageName,
                                    @Nonnull String recordName,
                                    boolean nullableChain) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponentType(enclosed).orElse(null);
            if (recordComponent == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            String fieldTypeName = getTypeName(fieldType, packageName);

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;

                boolean inline = !isDataType(recordElement, fieldName);
                boolean childNullableChain = nullableChain || isNullableField(recordElement, fieldName);
                builder.append("    /** Represents the ").append(inline ? "inline " : "")
                        .append("{@link ").append(recordName).append("#").append(fieldName).append("} ")
                        .append(inline ? "record." : "foreign key.").append(" */\n");
                builder.append("    public final ").append(metamodelClassName(fieldTypeName, childNullableChain))
                        .append("<T> ").append(fieldName)
                        .append(";\n");
            } else if (isRefType(fieldType)) {
                // A Ref<X> foreign key: a reference metamodel that selects the foreign key column but also navigates
                // beyond the reference (its children are navigation-only, so value extraction there does not compile).
                builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName)
                        .append("} reference. */\n");
                builder.append("    public final ").append(refClassName(fieldTypeName))
                        .append("<T> ").append(fieldName)
                        .append(";\n");
            } else {
                String valueTypeName = getValueTypeName(getDeclaredTypeElement(recordElement, fieldName), packageName);
                boolean unique = isEffectivelyUniqueField(recordElement, fieldName);
                boolean isData = implementsData(recordElement);
                String baseClass = (!isData || unique) ? "AbstractKeyMetamodel" : "AbstractMetamodel";

                builder.append("    /** Represents the {@link ").append(recordName).append("#").append(fieldName)
                        .append("} field. */\n");
                builder.append("    public final ").append(baseClass).append("<T, ").append(fieldTypeName).append(", ")
                        .append(valueTypeName).append("> ").append(fieldName)
                        .append(";\n");
            }
        }
        return builder.toString();
    }

    private String initClassFields(@Nonnull Element recordElement,
                                   @Nonnull String packageName,
                                   @Nonnull String recordName,
                                   @Nonnull String metaClassName,
                                   boolean nullableChain) {
        StringBuilder builder = new StringBuilder();

        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponentType(enclosed).orElse(null);
            if (recordComponent == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            String fieldTypeName = getTypeName(fieldType, packageName);
            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;

                boolean inline = !isDataType(recordElement, fieldName);
                boolean childNullableChain = nullableChain || isNullableField(recordElement, fieldName);
                String childMetamodel = metamodelClassName(fieldTypeName, childNullableChain);
                // Validate: @PK, @FK, and @UK are not supported on inline record fields. Both chain variants walk
                // the same fields; only the base pass reports, so a diagnostic prints once.
                if (!nullableChain && !implementsData(recordElement)) {
                    if (hasAnnotationOrMeta(enclosed, PRIMARY_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@PK is not supported on inline record fields. "
                                + "Primary keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                    if (hasAnnotationOrMeta(enclosed, FOREIGN_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@FK is not supported on inline record fields. "
                                + "Foreign keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                    if (hasAnnotationOrMeta(enclosed, UNIQUE_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@UK is not supported on inline record fields. "
                                + "Unique keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                }
                String inlineFlag = inline ? "true" : "false";
                // Null-safe nested getter: parent record (root getter) can be null.
                String nestedGetter =
                        "t -> {\n" +
                                "            " + recordName + " p = " + metaClassName + ".this.getValue(t);\n" +
                                "            return (p == null) ? null : " + accessorExpr(recordElement, "p", fieldName, fieldType) + ";\n" +
                                "        }";
                if (inline && isEffectivelyUniqueField(recordElement, fieldName)) {
                    boolean nullsDistinct = getNullsDistinct(recordElement, fieldName);
                    if (!nullableChain && nullsDistinct && hasNullableLeaf(asTypeElement(fieldType))) {
                        processingEnv.getMessager().printMessage(
                                WARNING,
                                "Unique key field '" + fieldName + "' on " + recordName + " has nullable constituent fields. "
                                + "Scrolling (scroll/scrollAfter/scrollBefore) will be rejected at runtime. "
                                + "Consider adding @Nonnull to constituent fields, using primitive types, or setting "
                                + "@UK(nullsDistinct = false) if the database constraint prevents duplicate NULLs.",
                                enclosed);
                    }
                    builder.append("        this.").append(fieldName).append(" = new ").append(childMetamodel)
                            .append("<>(")
                            .append("subPath, fieldBase + \"").append(fieldName).append("\", ")
                            .append(inlineFlag).append(", this, ")
                            .append(nestedGetter).append(", ").append(nullsDistinct)
                            .append(");\n");
                } else {
                    builder.append("        this.").append(fieldName).append(" = new ").append(childMetamodel)
                            .append("<>(")
                            .append("subPath, fieldBase + \"").append(fieldName).append("\", ")
                            .append(inlineFlag).append(", this, ")
                            .append(nestedGetter)
                            .append(");\n");
                }
            } else if (isRefType(fieldType)) {
                // A reference: the reference metamodel selects the foreign key column (its getValue returns the Ref)
                // and exposes navigation-only children beyond the reference. The getter yields the Ref value directly.
                String refGetter =
                        "t -> {\n" +
                                "            " + recordName + " p = " + metaClassName + ".this.getValue(t);\n" +
                                "            return (p == null) ? null : " + accessorExpr(recordElement, "p", fieldName, fieldType) + ";\n" +
                                "        }";
                builder.append("        this.").append(fieldName).append(" = new ")
                        .append(refClassName(fieldTypeName))
                        .append("<>(")
                        .append("subPath, fieldBase + \"").append(fieldName).append("\", false, this, ")
                        .append(refGetter)
                        .append(");\n");
            } else {
                String valueTypeName = getValueTypeName(getDeclaredTypeElement(recordElement, fieldName), packageName);
                boolean unique = isEffectivelyUniqueField(recordElement, fieldName);
                boolean isData = implementsData(recordElement);
                // Validate: @PK, @FK, and @UK are not supported on inline record fields. Both chain variants walk
                // the same fields; only the base pass reports, so a diagnostic prints once.
                if (!nullableChain && !isData) {
                    if (hasAnnotationOrMeta(enclosed, PRIMARY_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@PK is not supported on inline record fields. "
                                + "Primary keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                    if (hasAnnotationOrMeta(enclosed, FOREIGN_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@FK is not supported on inline record fields. "
                                + "Foreign keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                    if (hasAnnotationOrMeta(enclosed, UNIQUE_KEY)) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "@UK is not supported on inline record fields. "
                                + "Unique keys are only supported on top-level entity fields.",
                                enclosed);
                    }
                }
                String baseClass = (!isData || unique) ? "AbstractKeyMetamodel" : "AbstractMetamodel";
                boolean effectivelyNullable = false;
                if (!isData) {
                    // Leaf of a compound key: report raw field nullability for runtime derivation.
                    effectivelyNullable = isNullableUniqueField(recordElement, fieldName);
                } else if (unique) {
                    boolean nullable = isNullableUniqueField(recordElement, fieldName);
                    boolean nullsDistinct = getNullsDistinct(recordElement, fieldName);
                    effectivelyNullable = nullable && nullsDistinct;
                    if (!nullableChain && effectivelyNullable) {
                        processingEnv.getMessager().printMessage(
                                WARNING,
                                "Unique key field '" + fieldName + "' on " + recordName + " is nullable. "
                                + "Scrolling (scroll/scrollAfter/scrollBefore) will be rejected at runtime. "
                                + "Consider adding @Nonnull, using a primitive type, or setting @UK(nullsDistinct = false) "
                                + "if the database constraint prevents duplicate NULLs.",
                                enclosed);
                    }
                }
                String ownerA = metaClassName + ".this.getValue(a)";
                String ownerB = metaClassName + ".this.getValue(b)";
                String leftValue = accessorExpr(recordElement, "ra", fieldName, fieldType);
                String rightValue = accessorExpr(recordElement, "rb", fieldName, fieldType);
                String sameExpr = sameComparisonExpr(leftValue, rightValue, fieldType);
                String identicalExpr = identicalComparisonExpr(leftValue, rightValue, fieldType);
                String accOnOwner = accessorExpr(recordElement, "r", fieldName, fieldType);
                String constructorArgs;
                if (!isData || unique) {
                    constructorArgs = fieldTypeName + ".class, subPath, fieldBase + \"" + fieldName + "\", false, this, true, " + effectivelyNullable;
                } else {
                    constructorArgs = fieldTypeName + ".class, subPath, fieldBase + \"" + fieldName + "\", false, this";
                }
                builder.append("        this.").append(fieldName).append(" = new ").append(baseClass).append("<T, ")
                        .append(fieldTypeName).append(", ").append(valueTypeName).append(">(")
                        .append(constructorArgs).append(") {\n")
                        .append("            @Override public ").append(valueTypeName).append(" getValue(@Nonnull T record) {\n")
                        .append("                ").append(recordName).append(" r = ").append(metaClassName).append(".this.getValue(record);\n")
                        .append("                if (r == null) return null;\n")
                        .append("                return ").append(accOnOwner).append(";\n")
                        .append("            }\n\n")
                        .append("            @Override public boolean isIdentical(@Nonnull T a, @Nonnull T b) {\n")
                        .append("                ").append(recordName).append(" ra = ").append(ownerA).append(";\n")
                        .append("                ").append(recordName).append(" rb = ").append(ownerB).append(";\n")
                        .append("                if (ra == null || rb == null) return ra == rb;\n")
                        .append("                return ").append(identicalExpr).append(";\n")
                        .append("            }\n\n")
                        .append("            @Override public boolean isSame(@Nonnull T a, @Nonnull T b) {\n")
                        .append("                ").append(recordName).append(" ra = ").append(ownerA).append(";\n")
                        .append("                ").append(recordName).append(" rb = ").append(ownerB).append(";\n")
                        .append("                if (ra == null || rb == null) return ra == rb;\n")
                        .append("                return ").append(sameExpr).append(";\n")
                        .append("            }\n")
                        .append("        };\n");
            }
        }
        if (!builder.isEmpty()) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private String buildFlattenMethod(@Nonnull Element recordElement, boolean isData) {
        if (isData) {
            // A Data node names its own column(s): at the root it names the table, as a foreign key field it names
            // the foreign key column(s) on the referencing table. Column resolution expands it, so flatten does not
            // recurse into the referenced table.
            return "    @Override\n"
                    + "    public java.util.List<Metamodel<T, ?>> flatten() {\n"
                    + "        return java.util.List.<Metamodel<T, ?>>of(this);\n"
                    + "    }\n\n";
        }
        boolean hasInlineSubRecords = false;
        // Collect field entries: each is either "inline" (needs .flatten()) or "column" (add directly).
        List<String> fieldNames = new java.util.ArrayList<>();
        List<Boolean> fieldIsInline = new java.util.ArrayList<>();

        for (Element enclosed : recordElement.getEnclosedElements()) {
            TypeMirror recordComponent = getRecordComponentType(enclosed).orElse(null);
            if (recordComponent == null) continue;

            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                boolean inline = !isDataType(recordElement, fieldName);
                fieldNames.add(fieldName);
                fieldIsInline.add(inline);
                if (inline) hasInlineSubRecords = true;
            } else {
                fieldNames.add(fieldName);
                fieldIsInline.add(false);
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("    @Override\n");
        builder.append("    public java.util.List<Metamodel<T, ?>> flatten() {\n");

        if (!hasInlineSubRecords) {
            // Simple case: all fields are direct columns.
            builder.append("        return java.util.List.of(");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append("this.").append(fieldNames.get(i));
            }
            builder.append(");\n");
        } else {
            // Complex case: some fields are nested inline records.
            builder.append("        java.util.List<Metamodel<T, ?>> result = new java.util.ArrayList<>();\n");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (fieldIsInline.get(i)) {
                    builder.append("        result.addAll(this.").append(fieldNames.get(i)).append(".flatten());\n");
                } else {
                    builder.append("        result.add(this.").append(fieldNames.get(i)).append(");\n");
                }
            }
            builder.append("        return java.util.Collections.unmodifiableList(result);\n");
        }

        builder.append("    }\n\n");
        return builder.toString();
    }

    // ---- Reference and navigation-only metamodel generation ----

    private static String refClassName(@Nonnull String recordName) {
        return recordName + "RefMetamodel";
    }

    /**
     * Returns the metamodel class name for a record name. The nullable-chain variant matches the KSP output, so a
     * field reads as the same static type from Java and Kotlin. A qualified record name keeps its qualifier; the
     * variant lives in the record's package.
     */
    private static String metamodelClassName(@Nonnull String recordName, boolean nullableChain) {
        return recordName + (nullableChain ? "NullableMetamodel" : "Metamodel");
    }

    /**
     * Returns the navigation metamodel class name for a record name. The record name is qualified when the record is
     * declared in another package, and the navigation metamodel is generated into that same package, so the prefix
     * applies to the simple name and the qualifier is preserved.
     */
    private static String navClassName(@Nonnull String recordName) {
        int lastDot = recordName.lastIndexOf('.');
        return lastDot < 0
                ? "Navigable" + recordName + "Metamodel"
                : recordName.substring(0, lastDot + 1) + "Navigable" + recordName.substring(lastDot + 1) + "Metamodel";
    }

    private static TypeMirror unwrapRefType(@Nonnull TypeMirror fieldType) {
        if (fieldType instanceof DeclaredType declaredType && !declaredType.getTypeArguments().isEmpty()) {
            return declaredType.getTypeArguments().get(0);
        }
        return fieldType;
    }

    /**
     * Generates the reference metamodel for a {@code Ref<X>} foreign key target and the navigation-only metamodels for
     * the whole graph reachable beyond it.
     */
    private void generateReferenceArtifacts(@Nonnull Element refTarget) {
        TypeElement typeElement = asTypeElement(refTarget.asType());
        if (typeElement == null) return;
        if (generatedReferenceMetamodels.add(typeElement.getQualifiedName().toString())) {
            generateReferenceMetamodelClass(refTarget);
        }
        generateNavigableArtifacts(refTarget);
    }

    /**
     * Generates the navigation-only metamodel for a type and, transitively, for every record and reference it reaches,
     * so a path can navigate arbitrarily far beyond a reference.
     */
    private void generateNavigableArtifacts(@Nonnull Element element) {
        TypeElement typeElement = asTypeElement(element.asType());
        if (typeElement == null) return;
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (!generatedNavigableMetamodels.add(qualifiedName)) return;
        navPath.add(qualifiedName);
        try {
            generateNavigableMetamodelClass(element);
            for (Element enclosed : element.getEnclosedElements()) {
                if (getRecordComponentType(enclosed).isEmpty()) continue;
                String fieldName = enclosed.getSimpleName().toString();
                TypeMirror fieldType = getTypeElement(element, fieldName);
                if (fieldType == null) continue;
                if (isRecord(fieldType) && !isRefType(fieldType)) {
                    if (isNestedRecord(fieldType)) continue;
                    TypeElement childElement = asTypeElement(fieldType);
                    if (childElement != null) generateNavigableArtifacts(childElement);
                } else if (isRefType(fieldType)) {
                    TypeElement childElement = asTypeElement(unwrapRefType(fieldType));
                    if (childElement != null) generateNavigableArtifacts(childElement);
                }
            }
        } finally {
            navPath.remove(qualifiedName);
        }
    }

    /**
     * Returns whether navigating into {@code fieldType} would re-enter a type already being expanded, which would make
     * eager construction of the generated metamodels recurse forever. Such a child is emitted as a navigation leaf.
     */
    private boolean isCyclicNavChild(@Nonnull TypeMirror fieldType) {
        TypeMirror target = isRefType(fieldType) ? unwrapRefType(fieldType) : fieldType;
        TypeElement targetElement = asTypeElement(target);
        return targetElement != null && navPath.contains(targetElement.getQualifiedName().toString());
    }

    private String buildNavClassFields(@Nonnull Element recordElement, @Nonnull String packageName, @Nonnull String recordName) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (getRecordComponentType(enclosed).isEmpty()) continue;
            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;
            String fieldTypeName = getTypeName(fieldType, packageName);
            builder.append("    /** Represents navigation to {@link ").append(recordName).append("#").append(fieldName).append("}. */\n");
            boolean record = isRecord(fieldType) && !isRefType(fieldType);
            boolean ref = isRefType(fieldType);
            if (record && isNestedRecord(fieldType)) continue;
            if ((record || ref) && !isCyclicNavChild(fieldType)) {
                builder.append("    public final ").append(navClassName(fieldTypeName)).append("<T> ").append(fieldName).append(";\n");
            } else {
                // Scalar column, or a cyclic navigation edge broken to a leaf.
                builder.append("    public final st.orm.AbstractNavigableMetamodel<T, ").append(fieldTypeName).append("> ").append(fieldName).append(";\n");
            }
        }
        return builder.toString();
    }

    private String initNavClassFields(@Nonnull Element recordElement, @Nonnull String packageName) {
        StringBuilder builder = new StringBuilder();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (getRecordComponentType(enclosed).isEmpty()) continue;
            String fieldName = enclosed.getSimpleName().toString();
            TypeMirror fieldType = getTypeElement(recordElement, fieldName);
            if (fieldType == null) continue;
            String fieldTypeName = getTypeName(fieldType, packageName);
            boolean record = isRecord(fieldType) && !isRefType(fieldType);
            boolean ref = isRefType(fieldType);
            if (record && isNestedRecord(fieldType)) continue;
            if ((record || ref) && !isCyclicNavChild(fieldType)) {
                boolean inline = record && !isDataType(recordElement, fieldName);
                builder.append("        this.").append(fieldName).append(" = new ").append(navClassName(fieldTypeName))
                        .append("<>(subPath, fieldBase + \"").append(fieldName).append("\", ").append(inline ? "true" : "false").append(", this);\n");
            } else {
                // Scalar column, or a cyclic navigation edge broken to a leaf so eager construction terminates.
                builder.append("        this.").append(fieldName).append(" = new st.orm.AbstractNavigableMetamodel<T, ")
                        .append(fieldTypeName).append(">(").append(fieldTypeName).append(".class, subPath, fieldBase + \"")
                        .append(fieldName).append("\", false, this) {};\n");
            }
        }
        if (!builder.isEmpty()) builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    private void generateNavigableMetamodelClass(@Nonnull Element recordElement) {
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaClassName = navClassName(recordName);
        String navFields = buildNavClassFields(recordElement, packageName, recordName);
        String initFields = initNavClassFields(recordElement, packageName);
        String content =
                (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") +
                        "import st.orm.Navigable;\n" +
                        "import st.orm.AbstractNavigableMetamodel;\n" +
                        "import jakarta.annotation.Nonnull;\n" +
                        "import javax.annotation.processing.Generated;\n\n" +
                        "/**\n * Navigation-only metamodel for " + recordName + ", used to navigate beyond a reference boundary.\n *\n" +
                        " * @param <T> the record type of the root table of the entity graph.\n */\n" +
                        "@Generated(\"" + getClass().getName() + "\")\n" +
                        "public final class " + metaClassName + "<T extends st.orm.Data> extends AbstractNavigableMetamodel<T, " + recordName + "> {\n\n" +
                        navFields + "\n" +
                        "    public " + metaClassName + "(@Nonnull String field, @Nonnull Navigable<T, ?> parent) {\n" +
                        "        this(\"\", field, false, parent);\n" +
                        "    }\n\n" +
                        "    public " + metaClassName + "(@Nonnull String path, @Nonnull String field, boolean inline, @Nonnull Navigable<T, ?> parent) {\n" +
                        "        super(" + recordName + ".class, path, field, inline, parent);\n" +
                        "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : path + \".\" + field;\n" +
                        "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                        initFields + "\n" +
                        "    }\n" +
                        "}\n";
        writeSourceFile(packageName, metaClassName, recordElement, content);
    }

    private void generateReferenceMetamodelClass(@Nonnull Element recordElement) {
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaClassName = refClassName(recordName);
        String navFields = buildNavClassFields(recordElement, packageName, recordName);
        String initFields = initNavClassFields(recordElement, packageName);
        String refType = "st.orm.Ref<" + recordName + ">";
        String content =
                (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") +
                        "import st.orm.Metamodel;\n" +
                        "import st.orm.AbstractMetamodel;\n" +
                        "import jakarta.annotation.Nonnull;\n" +
                        "import jakarta.annotation.Nullable;\n" +
                        "import javax.annotation.processing.Generated;\n\n" +
                        "/**\n * Reference metamodel for " + recordName + ": selects the foreign key column and navigates beyond the reference.\n *\n" +
                        " * @param <T> the record type of the root table of the entity graph.\n */\n" +
                        "@Generated(\"" + getClass().getName() + "\")\n" +
                        "public final class " + metaClassName + "<T extends st.orm.Data> extends AbstractMetamodel<T, " + recordName + ", " + refType + "> {\n\n" +
                        navFields + "\n" +
                        "    private final java.util.function.Function<T, " + refType + "> getter;\n\n" +
                        "    /**\n     * Returns the reference the record holds, or {@code null} when the foreign key is null.\n     */\n" +
                        "    @Override\n    @Nullable\n    public " + refType + " getValue(@Nonnull T record) {\n        return getter.apply(record);\n    }\n\n" +
                        "    @Override\n    public boolean isIdentical(@Nonnull T a, @Nonnull T b) {\n        return getter.apply(a) == getter.apply(b);\n    }\n\n" +
                        "    @Override\n    public boolean isSame(@Nonnull T a, @Nonnull T b) {\n        return java.util.Objects.equals(getter.apply(a), getter.apply(b));\n    }\n\n" +
                        "    public " + metaClassName + "(@Nonnull String path, @Nonnull String field, boolean inline, @Nonnull Metamodel<T, ?> parent, @Nonnull java.util.function.Function<T, " + refType + "> getter) {\n" +
                        "        super(" + recordName + ".class, path, field, inline, parent);\n" +
                        "        this.getter = getter;\n" +
                        "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : path + \".\" + field;\n" +
                        "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                        initFields + "\n" +
                        "    }\n" +
                        "}\n";
        writeSourceFile(packageName, metaClassName, recordElement, content);
    }

    private void writeSourceFile(@Nonnull String packageName, @Nonnull String className, @Nonnull Element originating, @Nonnull String content) {
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + className, originating);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(content);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + className, e);
        }
    }

    private void generateMetamodelClass(@Nonnull Element recordElement, boolean nullableChain) {
        String packageName = elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String metaClassName = metamodelClassName(recordName, nullableChain);
        boolean isData = implementsData(recordElement);

        // Root isSame: compare by PK if present, else compare by value, but guard for null root record.
        Optional<String> pkNameOpt = findPrimaryKeyFieldName(recordElement);
        String rootIsSameBody;
        if (pkNameOpt.isPresent()) {
            String pkName = pkNameOpt.get();
            TypeMirror pkType = getTypeElement(recordElement, pkName);
            if (pkType == null) {
                if (!nullableChain) {
                    processingEnv.getMessager().printMessage(ERROR,
                            "Found @PK on '" + pkName + "' but could not resolve its type on " + recordName);
                }
                rootIsSameBody =
                        recordName + " ra = getter.apply(a);\n" +
                                "        " + recordName + " rb = getter.apply(b);\n" +
                                "        if (ra == null || rb == null) return ra == rb;\n" +
                                "        return Objects.equals(ra, rb);";
            } else {
                String left = accessorExpr(recordElement, "ra", pkName, pkType);
                String right = accessorExpr(recordElement, "rb", pkName, pkType);
                rootIsSameBody =
                        recordName + " ra = getter.apply(a);\n" +
                                "        " + recordName + " rb = getter.apply(b);\n" +
                                "        if (ra == null || rb == null) return ra == rb;\n" +
                                "        return " + sameComparisonExpr(left, right, pkType) + ";";
            }
        } else {
            rootIsSameBody =
                    recordName + " ra = getter.apply(a);\n" +
                            "        " + recordName + " rb = getter.apply(b);\n" +
                            "        if (ra == null || rb == null) return ra == rb;\n" +
                            "        return Objects.equals(ra, rb);";
        }

        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + metaClassName, recordElement);

            String classFields = buildClassFields(recordElement, packageName, recordName, nullableChain);
            String initFields = initClassFields(recordElement, packageName, recordName, metaClassName, nullableChain);

            String header =
                    (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") +
                            "import st.orm.Metamodel;\n" +
                            "import st.orm.AbstractMetamodel;\n" +
                            "import st.orm.AbstractKeyMetamodel;\n" +
                            "import jakarta.annotation.Nonnull;\n" +
                            "import javax.annotation.processing.Generated;\n" +
                            "import java.util.Objects;\n\n" +
                            "/**\n" +
                            (nullableChain
                                    ? " * Nullable-chain metamodel implementation for " + recordName
                                            + ": a parent in the graph can be null, so every value read through it can be.\n"
                                    : " * Metamodel implementation for " + recordName + ".\n") +
                            " *\n" +
                            " * @param <T> the record type of the root table of the entity graph.\n" +
                            " */\n" +
                            "@Generated(\"" + getClass().getName() + "\")\n" +
                            "public final class " + metaClassName + "<T extends st.orm.Data> extends " + (isData ? "AbstractMetamodel" : "AbstractKeyMetamodel") + "<T, " + recordName + ", " + recordName + "> {\n\n";

            String flattenMethod = buildFlattenMethod(recordElement, isData);

            String isNullableOverride = "";
            if (!isData) {
                isNullableOverride =
                        "    @Override\n" +
                        "    @SuppressWarnings(\"rawtypes\")\n" +
                        "    public boolean isNullable() {\n" +
                        "        if (!super.isNullable()) return false;\n" +
                        "        for (var leaf : flatten()) {\n" +
                        "            if (leaf instanceof Metamodel.Key key && key.isNullable()) return true;\n" +
                        "        }\n" +
                        "        return false;\n" +
                        "    }\n\n";
            }

            String body =
                    classFields + "\n" +
                            "    private final java.util.function.Function<T, " + recordName + "> getter;\n\n" +
                            "    @Override\n" +
                            "    public " + recordName + " getValue(@Nonnull T record) {\n" +
                            "        return getter.apply(record);\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public boolean isIdentical(@Nonnull T a, @Nonnull T b) {\n" +
                            "        " + recordName + " ra = getter.apply(a);\n" +
                            "        " + recordName + " rb = getter.apply(b);\n" +
                            "        return ra == rb;\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public boolean isSame(@Nonnull T a, @Nonnull T b) {\n" +
                            "        " + rootIsSameBody + "\n" +
                            "    }\n\n" +
                            flattenMethod +
                            isNullableOverride;
            String constructors;
            if (isData) {
                constructors =
                        "    public " + metaClassName + "() {\n" +
                                "        this(\"\", \"\", false, (Metamodel<T, ?>) Metamodel.root(" + recordName + ".class), " +
                                "t -> (" + recordName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String field, Metamodel<T, ?> parent) {\n" +
                                "        this(\"\", field, false, parent, t -> (" + recordName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent) {\n" +
                                "        this(path, field, false, parent, t -> (" + recordName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + recordName + "> getter) {\n" +
                                "        this(path, field, false, parent, getter);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent) {\n" +
                                "        this(path, field, inline, parent, t -> (" + recordName + ") t);\n" +
                                "    }\n\n";
            } else {
                constructors =
                        "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + recordName + "> getter) {\n" +
                                "        this(path, field, false, parent, getter);\n" +
                                "    }\n\n";
            }
            String fullCtor;
            if (isData) {
                fullCtor =
                        "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + recordName + "> getter) {\n" +
                                "        super(" + recordName + ".class, path, field, inline, parent);\n" +
                                "        this.getter = getter;\n\n" +
                                "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : " +
                                "path + \".\" + field;\n" +
                                "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                                initFields + "\n" +
                                "    }\n";
            } else {
                fullCtor =
                        "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + recordName + "> getter, boolean nullable) {\n" +
                                "        super(" + recordName + ".class, path, field, inline, parent, !inline && !field.isEmpty(), nullable);\n" +
                                "        this.getter = getter;\n\n" +
                                "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : " +
                                "path + \".\" + field;\n" +
                                "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                                initFields + "\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + recordName + "> getter) {\n" +
                                "        this(path, field, inline, parent, getter, false);\n" +
                                "    }\n";
            }
            String staticInstance = "";
            if (isData && !nullableChain) {
                staticInstance =
                        "\n    @SuppressWarnings(\"rawtypes\")\n" +
                        "    private static final " + metaClassName + " INSTANCE = new " + metaClassName + "();\n\n" +
                        "    @SuppressWarnings(\"unchecked\")\n" +
                        "    public static <T extends st.orm.Data> " + metaClassName + "<T> instance() {\n" +
                        "        return INSTANCE;\n" +
                        "    }\n";
            }
            String footer = "}\n";
            try (Writer writer = fileObject.openWriter()) {
                writer.write(header);
                writer.write(body);
                writer.write(constructors);
                writer.write(fullCtor);
                writer.write(staticInstance);
                writer.write(footer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + metaClassName, e);
        }
    }

    // ---- Sealed interface support ----

    private static boolean hasAnnotation(@Nonnull Element element, @Nonnull String annotationFqn) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(am -> annotationFqn.equals(am.getAnnotationType().toString()));
    }

    private static List<ExecutableElement> getDeclaredAbstractGetters(@Nonnull TypeElement sealedInterface) {
        return sealedInterface.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> m.getModifiers().contains(javax.lang.model.element.Modifier.ABSTRACT))
                .filter(m -> m.getParameters().isEmpty())
                .filter(m -> m.getReturnType().getKind() != TypeKind.VOID)
                .toList();
    }

    @Nullable
    private TypeElement getFirstPermittedRecord(@Nonnull TypeElement sealedInterface) {
        for (TypeMirror permitted : sealedInterface.getPermittedSubclasses()) {
            TypeElement sub = asTypeElement(permitted);
            if (sub != null && isRecord(sub)) {
                return sub;
            }
        }
        return null;
    }

    private boolean isPrimaryKeyOnSubclass(@Nonnull TypeElement sealedInterface, @Nonnull String fieldName) {
        TypeElement firstRecord = getFirstPermittedRecord(sealedInterface);
        return firstRecord != null && isPrimaryKeyField(firstRecord, fieldName);
    }

    private boolean isUniqueFieldOnSubclass(@Nonnull TypeElement sealedInterface, @Nonnull String fieldName) {
        TypeElement firstRecord = getFirstPermittedRecord(sealedInterface);
        return firstRecord != null && isUniqueField(firstRecord, fieldName);
    }

    /**
     * Returns {@code true} if the field should be treated as a unique key for metamodel generation purposes.
     */
    private static boolean isEffectivelyUniqueField(@Nonnull Element recordElement, @Nonnull String fieldName) {
        return isUniqueField(recordElement, fieldName);
    }

    private boolean isEffectivelyUniqueFieldOnSubclass(@Nonnull TypeElement sealedInterface, @Nonnull String fieldName) {
        TypeElement firstRecord = getFirstPermittedRecord(sealedInterface);
        return firstRecord != null && isEffectivelyUniqueField(firstRecord, fieldName);
    }

    private boolean getNullsDistinctOnSubclass(@Nonnull TypeElement sealedInterface, @Nonnull String fieldName) {
        TypeElement firstRecord = getFirstPermittedRecord(sealedInterface);
        return firstRecord != null ? getNullsDistinct(firstRecord, fieldName) : true;
    }

    private boolean isNullableOnSubclass(@Nonnull TypeElement sealedInterface, @Nonnull String fieldName) {
        TypeElement firstRecord = getFirstPermittedRecord(sealedInterface);
        return firstRecord != null && isNullableUniqueField(firstRecord, fieldName);
    }

    /**
     * Returns whether the getter carries any of the given annotations, checking both the method and the return
     * type use (JSpecify annotations annotate the type rather than the declaration).
     */
    private static boolean hasAnyReturnAnnotation(@Nonnull ExecutableElement getter, @Nonnull Set<String> names) {
        for (AnnotationMirror am : getter.getAnnotationMirrors()) {
            if (names.contains(am.getAnnotationType().toString())) {
                return true;
            }
        }
        for (AnnotationMirror am : getter.getReturnType().getAnnotationMirrors()) {
            if (names.contains(am.getAnnotationType().toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the derived nullability of a sealed interface getter, matching the runtime contract: primitives are
     * never null, explicit annotations win (nullable before non-null), and unannotated getters are non-null unless
     * a {@code @NullUnmarked} scope applies.
     */
    private static boolean isNullableGetter(@Nonnull ExecutableElement getter) {
        if (isPrimitiveReturn(getter.getReturnType())) return false;
        if (hasAnyReturnAnnotation(getter, NULLABLE_ANNOTATIONS)) return true;
        if (hasAnyReturnAnnotation(getter, NONNULL_ANNOTATIONS)) return false;
        return isNullUnmarkedScope(getter);
    }

    private void generateSealedMetamodelArtifacts(@Nonnull TypeElement sealedInterface,
                                                   @Nonnull List<ExecutableElement> declaredGetters) {
        String qn = sealedInterface.getQualifiedName().toString();
        boolean isData = implementsData(sealedInterface);

        if (generatedMetamodelClasses.add(qn)) {
            generateSealedMetamodelClass(sealedInterface, declaredGetters, false);
            generateSealedMetamodelClass(sealedInterface, declaredGetters, true);
        }

        if (isData && generatedMetamodelInterfaces.add(qn)) {
            generateSealedMetamodelInterface(sealedInterface, declaredGetters);
        }
    }

    private void generateSealedMetamodelInterface(@Nonnull TypeElement sealedInterface,
                                                   @Nonnull List<ExecutableElement> declaredGetters) {
        String packageName = elementUtils.getPackageOf(sealedInterface).getQualifiedName().toString();
        String typeName = sealedInterface.getSimpleName().toString();
        String metaInterfaceName = typeName + "_";

        StringBuilder fields = new StringBuilder();
        String modelRef = typeName + "Metamodel.<" + typeName + ">instance()";
        for (ExecutableElement getter : declaredGetters) {
            String fieldName = getter.getSimpleName().toString();
            TypeMirror fieldType = getter.getReturnType();
            String fieldTypeName = getTypeName(fieldType, packageName);

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                TypeElement nestedTypeEl = asTypeElement(fieldType);
                if (nestedTypeEl != null) {
                    generateMetamodelArtifacts(nestedTypeEl);
                }
                fields.append("    /** Represents the {@link ").append(typeName).append("#").append(fieldName)
                        .append("()} record. */\n");
                fields.append("    ").append(metamodelClassName(fieldTypeName, isNullableGetter(getter)))
                        .append("<").append(typeName).append("> ")
                        .append(fieldName).append(" = ").append(modelRef).append(".")
                        .append(fieldName).append(";\n");
            } else {
                String valueTypeName = getValueTypeName(fieldType, packageName);
                boolean unique = isEffectivelyUniqueFieldOnSubclass(sealedInterface, fieldName);
                String baseClass = unique ? "AbstractKeyMetamodel" : "AbstractMetamodel";
                fields.append("    /** Represents the {@link ").append(typeName).append("#").append(fieldName)
                        .append("()} field. */\n");
                fields.append("    ").append(baseClass).append("<").append(typeName).append(", ").append(fieldTypeName)
                        .append(", ").append(valueTypeName).append("> ")
                        .append(fieldName).append(" = ").append(modelRef).append(".")
                        .append(fieldName).append(";\n");
            }
        }
        if (!fields.isEmpty()) {
            fields.setLength(fields.length() - 1);
        }

        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + metaInterfaceName, sealedInterface);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(String.format("""
                    %simport st.orm.Metamodel;
                    import st.orm.AbstractMetamodel;
                    import st.orm.AbstractKeyMetamodel;
                    import javax.annotation.processing.Generated;

                    /**
                     * Metamodel for %s.
                     */
                    @Generated("%s")
                    public interface %s extends Metamodel<%s, %s> {
                    %s
                    }""",
                        (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n"),
                        typeName,
                        getClass().getName(),
                        metaInterfaceName,
                        typeName,
                        typeName,
                        fields.toString()
                ));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + metaInterfaceName, e);
        }
    }

    private void generateSealedMetamodelClass(@Nonnull TypeElement sealedInterface,
                                               @Nonnull List<ExecutableElement> declaredGetters,
                                               boolean nullableChain) {
        String packageName = elementUtils.getPackageOf(sealedInterface).getQualifiedName().toString();
        String typeName = sealedInterface.getSimpleName().toString();
        String metaClassName = metamodelClassName(typeName, nullableChain);
        boolean isData = implementsData(sealedInterface);

        // Find PK via subclass.
        String pkName = null;
        TypeMirror pkType = null;
        for (ExecutableElement getter : declaredGetters) {
            String fieldName = getter.getSimpleName().toString();
            if (isPrimaryKeyOnSubclass(sealedInterface, fieldName)) {
                pkName = fieldName;
                pkType = getter.getReturnType();
                break;
            }
        }

        String rootIsSameBody;
        if (pkName != null && pkType != null) {
            String left = "ra." + pkName + "()";
            String right = "rb." + pkName + "()";
            rootIsSameBody =
                    typeName + " ra = getter.apply(a);\n" +
                            "        " + typeName + " rb = getter.apply(b);\n" +
                            "        if (ra == null || rb == null) return ra == rb;\n" +
                            "        return " + sameComparisonExpr(left, right, pkType) + ";";
        } else {
            rootIsSameBody =
                    typeName + " ra = getter.apply(a);\n" +
                            "        " + typeName + " rb = getter.apply(b);\n" +
                            "        if (ra == null || rb == null) return ra == rb;\n" +
                            "        return Objects.equals(ra, rb);";
        }

        // Build class fields.
        StringBuilder classFields = new StringBuilder();
        for (ExecutableElement getter : declaredGetters) {
            String fieldName = getter.getSimpleName().toString();
            TypeMirror fieldType = getter.getReturnType();
            String fieldTypeName = getTypeName(fieldType, packageName);

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                boolean inline = !implementsInterface(fieldType, DATA, typeUtils);
                boolean childNullableChain = nullableChain || isNullableGetter(getter);
                classFields.append("    /** Represents the ").append(inline ? "inline " : "")
                        .append("{@link ").append(typeName).append("#").append(fieldName).append("()} ")
                        .append(inline ? "record." : "foreign key.").append(" */\n");
                classFields.append("    public final ").append(metamodelClassName(fieldTypeName, childNullableChain))
                        .append("<T> ").append(fieldName)
                        .append(";\n");
            } else {
                String valueTypeName = getValueTypeName(fieldType, packageName);
                boolean unique = isEffectivelyUniqueFieldOnSubclass(sealedInterface, fieldName);
                String baseClass = (!isData || unique) ? "AbstractKeyMetamodel" : "AbstractMetamodel";
                classFields.append("    /** Represents the {@link ").append(typeName).append("#").append(fieldName)
                        .append("()} field. */\n");
                classFields.append("    public final ").append(baseClass).append("<T, ").append(fieldTypeName).append(", ")
                        .append(valueTypeName).append("> ").append(fieldName).append(";\n");
            }
        }

        // Build field initializations.
        StringBuilder initFields = new StringBuilder();
        for (ExecutableElement getter : declaredGetters) {
            String fieldName = getter.getSimpleName().toString();
            TypeMirror fieldType = getter.getReturnType();
            String fieldTypeName = getTypeName(fieldType, packageName);

            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                boolean inline = !implementsInterface(fieldType, DATA, typeUtils);
                boolean childNullableChain = nullableChain || isNullableGetter(getter);
                String childMetamodel = metamodelClassName(fieldTypeName, childNullableChain);
                String inlineFlag = inline ? "true" : "false";
                String nestedGetter =
                        "t -> {\n" +
                                "            " + typeName + " p = " + metaClassName + ".this.getValue(t);\n" +
                                "            return (p == null) ? null : p." + fieldName + "();\n" +
                                "        }";
                if (inline && isEffectivelyUniqueFieldOnSubclass(sealedInterface, fieldName)) {
                    boolean nullsDistinct = getNullsDistinctOnSubclass(sealedInterface, fieldName);
                    initFields.append("        this.").append(fieldName).append(" = new ").append(childMetamodel)
                            .append("<>(")
                            .append("subPath, fieldBase + \"").append(fieldName).append("\", ")
                            .append(inlineFlag).append(", this, ")
                            .append(nestedGetter).append(", ").append(nullsDistinct)
                            .append(");\n");
                } else {
                    initFields.append("        this.").append(fieldName).append(" = new ").append(childMetamodel)
                            .append("<>(")
                            .append("subPath, fieldBase + \"").append(fieldName).append("\", ")
                            .append(inlineFlag).append(", this, ")
                            .append(nestedGetter)
                            .append(");\n");
                }
            } else {
                String valueTypeName = getValueTypeName(fieldType, packageName);
                boolean unique = isEffectivelyUniqueFieldOnSubclass(sealedInterface, fieldName);
                String baseClass = (!isData || unique) ? "AbstractKeyMetamodel" : "AbstractMetamodel";
                boolean effectivelyNullable;
                if (!isData) {
                    effectivelyNullable = isNullableOnSubclass(sealedInterface, fieldName);
                } else if (unique) {
                    boolean nullable = isNullableOnSubclass(sealedInterface, fieldName);
                    boolean nullsDistinct = getNullsDistinctOnSubclass(sealedInterface, fieldName);
                    effectivelyNullable = nullable && nullsDistinct;
                } else {
                    effectivelyNullable = false;
                }
                String accessExpr = "r." + fieldName + "()";
                String leftValue = "ra." + fieldName + "()";
                String rightValue = "rb." + fieldName + "()";
                String sameExpr = sameComparisonExpr(leftValue, rightValue, fieldType);
                String identicalExpr = identicalComparisonExpr(leftValue, rightValue, fieldType);
                String constructorArgs;
                if (!isData || unique) {
                    constructorArgs = fieldTypeName + ".class, subPath, fieldBase + \"" + fieldName + "\", false, this, true, " + effectivelyNullable;
                } else {
                    constructorArgs = fieldTypeName + ".class, subPath, fieldBase + \"" + fieldName + "\", false, this";
                }
                initFields.append("        this.").append(fieldName).append(" = new ").append(baseClass).append("<T, ")
                        .append(fieldTypeName).append(", ").append(valueTypeName).append(">(")
                        .append(constructorArgs).append(") {\n")
                        .append("            @Override public ").append(valueTypeName).append(" getValue(@Nonnull T record) {\n")
                        .append("                ").append(typeName).append(" r = ").append(metaClassName).append(".this.getValue(record);\n")
                        .append("                if (r == null) return null;\n")
                        .append("                return ").append(accessExpr).append(";\n")
                        .append("            }\n\n")
                        .append("            @Override public boolean isIdentical(@Nonnull T a, @Nonnull T b) {\n")
                        .append("                ").append(typeName).append(" ra = ").append(metaClassName).append(".this.getValue(a);\n")
                        .append("                ").append(typeName).append(" rb = ").append(metaClassName).append(".this.getValue(b);\n")
                        .append("                if (ra == null || rb == null) return ra == rb;\n")
                        .append("                return ").append(identicalExpr).append(";\n")
                        .append("            }\n\n")
                        .append("            @Override public boolean isSame(@Nonnull T a, @Nonnull T b) {\n")
                        .append("                ").append(typeName).append(" ra = ").append(metaClassName).append(".this.getValue(a);\n")
                        .append("                ").append(typeName).append(" rb = ").append(metaClassName).append(".this.getValue(b);\n")
                        .append("                if (ra == null || rb == null) return ra == rb;\n")
                        .append("                return ").append(sameExpr).append(";\n")
                        .append("            }\n")
                        .append("        };\n");
            }
        }
        if (!initFields.isEmpty()) {
            initFields.setLength(initFields.length() - 1);
        }

        // Build flatten method. A Data node names its own column(s), so it flattens to itself; only a non-Data
        // (inline) node expands into its components.
        List<String> fieldNames = new java.util.ArrayList<>();
        List<Boolean> fieldIsInline = new java.util.ArrayList<>();
        boolean hasInlineSubRecords = false;
        for (ExecutableElement getter : declaredGetters) {
            String fieldName = getter.getSimpleName().toString();
            TypeMirror fieldType = getter.getReturnType();
            if (isRecord(fieldType) && !isRefType(fieldType)) {
                if (isNestedRecord(fieldType)) continue;
                boolean inline = !implementsInterface(fieldType, DATA, typeUtils);
                fieldNames.add(fieldName);
                fieldIsInline.add(inline);
                if (inline) hasInlineSubRecords = true;
            } else {
                fieldNames.add(fieldName);
                fieldIsInline.add(false);
            }
        }
        StringBuilder flattenMethod = new StringBuilder();
        flattenMethod.append("    @Override\n");
        flattenMethod.append("    public java.util.List<Metamodel<T, ?>> flatten() {\n");
        if (isData) {
            flattenMethod.append("        return java.util.List.<Metamodel<T, ?>>of(this);\n");
        } else if (!hasInlineSubRecords) {
            flattenMethod.append("        return java.util.List.of(");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) flattenMethod.append(", ");
                flattenMethod.append("this.").append(fieldNames.get(i));
            }
            flattenMethod.append(");\n");
        } else {
            flattenMethod.append("        java.util.List<Metamodel<T, ?>> result = new java.util.ArrayList<>();\n");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (fieldIsInline.get(i)) {
                    flattenMethod.append("        result.addAll(this.").append(fieldNames.get(i)).append(".flatten());\n");
                } else {
                    flattenMethod.append("        result.add(this.").append(fieldNames.get(i)).append(");\n");
                }
            }
            flattenMethod.append("        return java.util.Collections.unmodifiableList(result);\n");
        }
        flattenMethod.append("    }\n\n");

        // Assemble the class.
        try {
            JavaFileObject fileObject = processingEnv.getFiler()
                    .createSourceFile((packageName.isEmpty() ? "" : packageName + ".") + metaClassName, sealedInterface);

            String header =
                    (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") +
                            "import st.orm.Metamodel;\n" +
                            "import st.orm.AbstractMetamodel;\n" +
                            "import st.orm.AbstractKeyMetamodel;\n" +
                            "import jakarta.annotation.Nonnull;\n" +
                            "import javax.annotation.processing.Generated;\n" +
                            "import java.util.Objects;\n\n" +
                            "/**\n" +
                            (nullableChain
                                    ? " * Nullable-chain metamodel implementation for " + typeName
                                            + ": a parent in the graph can be null, so every value read through it can be.\n"
                                    : " * Metamodel implementation for " + typeName + ".\n") +
                            " *\n" +
                            " * @param <T> the record type of the root table of the entity graph.\n" +
                            " */\n" +
                            "@Generated(\"" + getClass().getName() + "\")\n" +
                            "public final class " + metaClassName + "<T extends st.orm.Data> extends " +
                            (isData ? "AbstractMetamodel" : "AbstractKeyMetamodel") + "<T, " + typeName + ", " + typeName + "> {\n\n";

            String body =
                    classFields + "\n" +
                            "    private final java.util.function.Function<T, " + typeName + "> getter;\n\n" +
                            "    @Override\n" +
                            "    public " + typeName + " getValue(@Nonnull T record) {\n" +
                            "        return getter.apply(record);\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public boolean isIdentical(@Nonnull T a, @Nonnull T b) {\n" +
                            "        " + typeName + " ra = getter.apply(a);\n" +
                            "        " + typeName + " rb = getter.apply(b);\n" +
                            "        return ra == rb;\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public boolean isSame(@Nonnull T a, @Nonnull T b) {\n" +
                            "        " + rootIsSameBody + "\n" +
                            "    }\n\n" +
                            flattenMethod;

            String constructors;
            if (isData) {
                constructors =
                        "    public " + metaClassName + "() {\n" +
                                "        this(\"\", \"\", false, (Metamodel<T, ?>) Metamodel.root(" + typeName + ".class), " +
                                "t -> (" + typeName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String field, Metamodel<T, ?> parent) {\n" +
                                "        this(\"\", field, false, parent, t -> (" + typeName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent) {\n" +
                                "        this(path, field, false, parent, t -> (" + typeName + ") t);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + typeName + "> getter) {\n" +
                                "        this(path, field, false, parent, getter);\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent) {\n" +
                                "        this(path, field, inline, parent, t -> (" + typeName + ") t);\n" +
                                "    }\n\n";
            } else {
                constructors =
                        "    public " + metaClassName + "(String path, String field, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + typeName + "> getter) {\n" +
                                "        this(path, field, false, parent, getter);\n" +
                                "    }\n\n";
            }

            String fullCtor;
            if (isData) {
                fullCtor =
                        "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + typeName + "> getter) {\n" +
                                "        super(" + typeName + ".class, path, field, inline, parent);\n" +
                                "        this.getter = getter;\n\n" +
                                "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : " +
                                "path + \".\" + field;\n" +
                                "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                                initFields + "\n" +
                                "    }\n";
            } else {
                fullCtor =
                        "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + typeName + "> getter, boolean nullable) {\n" +
                                "        super(" + typeName + ".class, path, field, inline, parent, !inline && !field.isEmpty(), nullable);\n" +
                                "        this.getter = getter;\n\n" +
                                "        String subPath = inline ? path : field.isEmpty() ? path : path.isEmpty() ? field : " +
                                "path + \".\" + field;\n" +
                                "        String fieldBase = inline ? (field.isEmpty() ? \"\" : field + \".\") : \"\";\n\n" +
                                initFields + "\n" +
                                "    }\n\n" +
                                "    public " + metaClassName + "(String path, String field, boolean inline, Metamodel<T, ?> parent, " +
                                "java.util.function.Function<T, " + typeName + "> getter) {\n" +
                                "        this(path, field, inline, parent, getter, false);\n" +
                                "    }\n";
            }
            String staticInstance = "";
            if (isData && !nullableChain) {
                staticInstance =
                        "\n    @SuppressWarnings(\"rawtypes\")\n" +
                        "    private static final " + metaClassName + " INSTANCE = new " + metaClassName + "();\n\n" +
                        "    @SuppressWarnings(\"unchecked\")\n" +
                        "    public static <T extends st.orm.Data> " + metaClassName + "<T> instance() {\n" +
                        "        return INSTANCE;\n" +
                        "    }\n";
            }
            String footer = "}\n";
            try (Writer writer = fileObject.openWriter()) {
                writer.write(header);
                writer.write(body);
                writer.write(constructors);
                writer.write(fullCtor);
                writer.write(staticInstance);
                writer.write(footer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + metaClassName, e);
        }
    }
}
