
package ru.surfstudio.ci

class AbortDuplicateStrategy {
    public static final String SELF = 'SELF'       //отменить текущий билд если есйчас выполняется другой такой же
    public static final String ANOTHER = 'ANOTHER' //отменить другие такие же билды, если они выполняются
}