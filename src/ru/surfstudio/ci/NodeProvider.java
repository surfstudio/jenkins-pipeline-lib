package ru.surfstudio.ci;

//https://jenkins.io/doc/pipeline/steps/workflow-durable-task-step/
public class NodeProvider {

    public static String getAndroidNode(){
        return "android"; //"android" - метка, поэтому будет использоваться один из доступных компьютеров с этой метокй
    }
}
