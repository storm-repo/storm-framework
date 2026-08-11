module storm.metamodel {
    requires java.compiler;
    requires static org.jspecify;
    provides javax.annotation.processing.Processor with st.orm.metamodel.MetamodelProcessor,st.orm.metamodel.TypeIndexProcessor;
}
