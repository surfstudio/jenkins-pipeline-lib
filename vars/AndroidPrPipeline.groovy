import ru.surfstudio.ci.PrContext

def call(PrContext ctx) {
    // Any valid steps can be called from this code, just like in other
    // Scripted Pipeline
    echo "Hello, ${name}."
}
