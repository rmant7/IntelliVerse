package com.calories

object OutputSizeException: RuntimeException() {
    private fun readResolve(): Any = OutputSizeException
}

object UnableToAssistException: RuntimeException() {
    private fun readResolve(): Any = UnableToAssistException
}