package org.autojs.autoxjs.mcp.tools

import org.autojs.autoxjs.mcp.McpLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class JobStatus(
    val jobId: Int,
    val name: String,
    val status: Status,
    val message: String? = null
) {
    enum class Status {
        SUBMITTED, RUNNING, SUCCESS, FAILED, CANCELED
    }
}

class JobTracker {
    private val idGen = AtomicInteger(1)
    private val jobs = ConcurrentHashMap<Int, JobStatus>()

    fun newJob(name: String): JobStatus {
        val id = idGen.getAndIncrement()
        val status = JobStatus(id, name, JobStatus.Status.SUBMITTED)
        jobs[id] = status
        McpLog.i(McpLog.TAG_JOB, "new jobId=$id name=$name status=SUBMITTED totalActive=${jobs.size}")
        return status
    }

    fun update(id: Int, status: JobStatus.Status, message: String? = null) {
        val existing = jobs[id]
        if (existing != null) {
            val prev = existing.status
            jobs[id] = existing.copy(status = status, message = message)
            McpLog.i(
                McpLog.TAG_JOB,
                "update jobId=$id $prev -> $status message=${message ?: "<none>"}"
            )
        } else {
            McpLog.w(McpLog.TAG_JOB, "update on unknown jobId=$id status=$status (ignored)")
        }
    }

    fun get(id: Int): JobStatus? = jobs[id]

    fun recent(limit: Int = 20): List<JobStatus> = jobs.values.sortedBy { it.jobId }.takeLast(limit)
}