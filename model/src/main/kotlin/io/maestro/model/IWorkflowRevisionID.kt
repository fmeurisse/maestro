package io.maestro.model

interface IWorkflowRevisionID {
    val namespace: String
    val id: String
    val version: Long
}