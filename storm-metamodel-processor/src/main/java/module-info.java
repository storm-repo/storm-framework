module storm.metamodel {
    requires java.compiler;
    provides javax.annotation.processing.Processor with st.orm.metamodel.MetamodelProcessor;
}