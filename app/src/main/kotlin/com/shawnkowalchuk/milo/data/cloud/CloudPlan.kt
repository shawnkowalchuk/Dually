package com.shawnkowalchuk.milo.data.cloud

/**
 * What one run of the backup is to do to bring the cloud to what the phone has.
 *
 * @param uploads the documents that are new or changed since they were last uploaded.
 * @param removals the paths that were uploaded and that the phone no longer has: a trip whose
 * row is gone, which happens only when an import or a restore replaces every trip.
 */
data class CloudPlan(val uploads: List<CloudDocument>, val removals: List<String>) {
    val isEmpty: Boolean get() = uploads.isEmpty() && removals.isEmpty()
}

/**
 * The plan for [snapshot], given [uploaded]: the fingerprint of each document as it was last
 * uploaded, by path.
 *
 * This comparison is how the backup learns of every change. There is no list of changes kept
 * as they happen: a trip is changed in many places (its end, its address, an edit, a label, a
 * delete, an import), and a comparison of what is with what was sent cannot miss one of them.
 * A path in [CloudSnapshot.unknown] is neither uploaded nor removed.
 */
fun cloudPlanFor(snapshot: CloudSnapshot, uploaded: Map<String, String>): CloudPlan {
    val present = snapshot.documents.mapTo(HashSet()) { it.path }
    return CloudPlan(
        uploads = snapshot.documents.filter { uploaded[it.path] != it.fingerprint },
        removals =
            uploaded.keys
                .filter { it !in present && it !in snapshot.unknown }
                .sorted(),
    )
}
