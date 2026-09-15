package dev.sophiel

import dev.sophiel.capture.ProjectionController

/**
 * Manual DI (SPEC.md §2.4/D2): two modules and a handful of app-scoped singletons don't
 * justify Hilt. [dev.sophiel.MainActivity] and [dev.sophiel.capture.ProjectionService] both
 * read [projectionController] off [SophielApp] so they observe/drive the same session even
 * though the service outlives any single Activity instance.
 */
class AppContainer {
    val projectionController = ProjectionController()
}
