module storm.metamodel {
    requires java.compiler;
    requires jakarta.annotation;
    provides javax.annotation.processing.Processor with st.orm.metamodel.MetamodelProcessor,st.orm.metamodel.TypeIndexProcessor;
}