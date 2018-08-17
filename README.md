# jenkins-pipeline-lib
[Библиотека](https://jenkins.io/doc/book/pipeline/shared-libraries/) для pipeline скриптов дженкинса


Наследники класса Pipeline - ключевые сущности для выполнения скрипта
По сути они знают что и как выполнять и определяют конфигурацию скрипта

* "как" определяется методом run (не нужно переопределять)
* "что" определяется в методе init (нужно переопределять)
* "конфигурация" определяется через публичные переменные в классах наследниках

Для создания собственного наследника необходимо переопределить метод init и в нем определенить переменные:

 * node
 * stages
 * finalizeBody

Предусмотрены различные способы кастомизации

 * изменение переменных, определяющих контекст
 * изменение стратегии/тела Stage (для получения следует использовать getStage())
 * замена целых Stage через метод replaceStage() или напрямую через переменную stages
 * все остальное, что может прийти в голову, так как все переменные публичные

 Наследники этого класса должны определять только общуую, высокоуровневую конфигурацию,
 детали реализации должны находиться в пакете stage.body в виде классов со статическими методами,
 следует делать их максимально чистыми и независимыми для возможности переиспользования без механизмов класса Pipeline

Предусмотрен класс EmptyPipeline для полостью кастомных скриптов

Пример импользования:
```groovy
@Library('surf-lib')
import ru.surfstudio.ci.pipeline.tag.TagPipelineAndroid
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.AndroidUtil

//init
def pipeline = new TagPipelineAndroid(this)
pipeline.init()

//customization
pipeline.buildGradleTask = "clean assembleQa"
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(pipeline.INSTRUMENTATION_TEST).body = {
	AndroidUtil.onEmulator(this, "your avd") {
		sh "./gradlew connectedTest"
	}
}

//run
pipeline.run()
```

###Шаблоны
В папке templates находятся стандартные Jenkins скрипты для разных типов проектов (android, ios, ui_test)

#Разработка
Изменение библиотеки следует производить в feature-branch, чтобы не затронуть работающие проекты. Когда новая функциональность будет закончена, следует слить ветку с ней в master. 
Для тестирования изменений в пайплайн скрипте следует импортировать библиотеку следующим образом `@Library('surf-lib@feature-branch')`. Не забываем поддерживать обратную совместимость Api библиотеки.
###Инструменты
Для работы с библиотекой удобно использовать [IntelliJ IDEA](https://www.jetbrains.com/idea/) c установленным [Groovy](http://groovy-lang.org/install.html).
В ProjectStructure на вкладке Modules директорию "src" следует пометить как "Sources" 
