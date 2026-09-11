package com.example.jobsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to collect high-quality training examples for future LoRA fine-tuning.
 */
@Entity(tableName = "training_examples")
data class TrainingExample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String, // "task" or "chatbot"
    val feature: String, // e.g. "create_subtasks", "organize_folder", "tag_conversation", "prepare_prompt_for_desktop"
    val inputPrompt: String,
    val modelOutput: String,
    val correctedOutput: String? = null,
    val isGoodExample: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val extraMetadata: String? = null // Optional JSON
)
