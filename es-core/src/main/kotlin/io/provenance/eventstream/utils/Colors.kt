package io.provenance.eventstream.utils.colors

private object AnsiColors {
    const val RESET = "\u001B[0m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val PURPLE = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"
}

fun red(input: String?) = AnsiColors.RED + input + AnsiColors.RESET
fun green(input: String?) = AnsiColors.GREEN + input + AnsiColors.RESET
fun yellow(input: String?) = AnsiColors.YELLOW + input + AnsiColors.RESET
fun blue(input: String?) = AnsiColors.BLUE + input + AnsiColors.RESET
fun purple(input: String?) = AnsiColors.PURPLE + input + AnsiColors.RESET
fun cyan(input: String?) = AnsiColors.CYAN + input + AnsiColors.RESET
fun white(input: String?) = AnsiColors.WHITE + input + AnsiColors.RESET
