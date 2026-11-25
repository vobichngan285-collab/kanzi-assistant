package com.vobich.bluestar.npulab
object LabProtocol {
    const val START=1
    const val STOP=2
    const val HELLO=3
    const val STAGE=4
    const val ROW=5
    const val RESULT=6
    const val DONE=7
    const val FAILED=8
    val modes=setOf("cpu","gpu_vision","gpu_all","npu_vision")
    fun deadlineMs(stage:String):Long=when(stage){
        "connect"->20000L
        "load","conversation"->120000L
        "send"->180000L
        "first_text"->120000L
        "close_conversation","close_engine"->30000L
        else->60000L
    }
}
