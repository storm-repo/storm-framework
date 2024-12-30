module storm.metamodel {
    requires java.compiler;
    requires storm;
    requires jakarta.annotation;
    provides javax.annotation.processing.Processor with st.orm.metamodel.MetamodelProcessor;
}