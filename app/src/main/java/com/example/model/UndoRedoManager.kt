package com.example.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder

class UndoRedoManager(private val onStateChange: (ProjectData) -> Unit) {
    private val gson: Gson = GsonBuilder().create()
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun saveState(project: ProjectData) {
        val state = gson.toJson(project)
        if (undoStack.isEmpty() || undoStack.last() != state) {
            undoStack.add(state)
            redoStack.clear()
        }
    }

    fun undo(currentProject: ProjectData): ProjectData? {
        if (undoStack.size <= 1) return null // Keep at least one state

        val currentState = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(currentState)

        val previousState = undoStack.last()
        return gson.fromJson(previousState, ProjectData::class.java)
    }

    fun redo(currentProject: ProjectData): ProjectData? {
        if (redoStack.isEmpty()) return null

        val nextState = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(nextState)

        return gson.fromJson(nextState, ProjectData::class.java)
    }

    fun canUndo() = undoStack.size > 1
    fun canRedo() = redoStack.isNotEmpty()
}
