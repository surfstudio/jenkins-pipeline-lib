package ru.surfstudio.ci.utils.buildsystems

final class GradleUtil {

    private static String DEFAULT_REGEX= /(;| |\t|=|,|:)/
    private static int GRADLE_KOTLIN_DSL_VERSION_VARIABLE_POSITION = 1
    private static int GRADLE_KOTLIN_DSL_VERSION_VALUE_POSITION = 2

    private static int GRADLE_GROOVY_DSL_VERSION_VARIABLE_POSITION = 0
    private static int GRADLE_GROOVY_DSL_VERSION_VALUE_POSITION = 1


    static final String GRADLE_BUILD_CACHE_CREDENTIALS_ID = "gradle_build_cache"


    private GradleUtil() {
    }

    static String getGradleVariableKtStyle(Object script, String file, String varName) {
        return getVariable(script, file, varName, DEFAULT_REGEX,GRADLE_KOTLIN_DSL_VERSION_VARIABLE_POSITION, GRADLE_KOTLIN_DSL_VERSION_VALUE_POSITION)
    }

    static String changeGradleVariableKtStyle(Object script, String file, String varName, String newVarValue) {
        String oldVarValue = getGradleVariableKtStyle(script, file, varName)
        changeVariableValue(script, file, varName, newVarValue, GRADLE_KOTLIN_DSL_VERSION_VARIABLE_POSITION, oldVarValue)
    }

    static String getGradleVariable(Object script, String file, String varName) {
        return getVariable(script, file, varName, DEFAULT_REGEX,GRADLE_GROOVY_DSL_VERSION_VARIABLE_POSITION, GRADLE_GROOVY_DSL_VERSION_VALUE_POSITION)
    }

    static String changeGradleVariable(Object script, String file, String varName, String newVarValue) {
        String oldVarValue = getGradleVariable(script, file, varName)
        changeVariableValue(script, file, varName, newVarValue, GRADLE_GROOVY_DSL_VERSION_VARIABLE_POSITION, oldVarValue)
    }

    private static void changeVariableValue(script, String file, String varName, String newVarValue, int variablePosition, String oldVarValue) {
        String fileBody = script.readFile(file)
        String newFileBody = ""
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(DEFAULT_REGEX).findAll({ it?.trim() })
            if (words[variablePosition] == varName) {
                String updatedLine = line.replace(oldVarValue, newVarValue)
                newFileBody += updatedLine
            } else {
                newFileBody += line
            }
            newFileBody += "\n"
        }
        script.writeFile file: file, text: newFileBody
        script.echo "$varName value changed to $newVarValue in file $file"
    }

    private static String getVariable(script, String file, String varName, String regex, int variablePosition, int valuePosition) {
        String fileBody = script.readFile(file)
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(regex).findAll({ it?.trim() })
            if (words[variablePosition] == varName && words.size() > 1) {
                def value = words[valuePosition]
                script.echo "$varName = $value found in file $file"
                return value
            }
        }
        throw script.error("groovy variable with name: $varName not exist in file: $file")
    }


    /**
     * Execute body with global variables 'GRADLE_BUILD_CACHE_USER' and 'GRADLE_BUILD_CACHE_PASS'
     */
    def static withGradleBuildCacheCredentials(Object script, Closure body) {
        script.withCredentials([
                script.usernamePassword(
                        credentialsId: GRADLE_BUILD_CACHE_CREDENTIALS_ID,
                        usernameVariable: 'GRADLE_BUILD_CACHE_USER',
                        passwordVariable: 'GRADLE_BUILD_CACHE_PASS')
        ]) {
            body()
        }
    }
}
