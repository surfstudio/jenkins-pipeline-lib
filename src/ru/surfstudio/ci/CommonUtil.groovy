package ru.surfstudio.ci

class CommonUtil {
    public static void applyParameterIfNotEmpty(BaseContext ctx, String varName, String param, assignmentAction) {
        if (param?.trim()) {
            ctx.script.echo "value of {$varName} sets from parameters to {$param}"
            assignmentAction(param)
        }
    }

    public static void printDefaultVar(BaseContext ctx, String varName, String varValue) {
        ctx.script.echo "default value of {$varName} is {$varValue}"
    }
}
