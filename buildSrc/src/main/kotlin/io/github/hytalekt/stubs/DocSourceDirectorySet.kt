package io.github.hytalekt.stubs

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.tasks.TaskDependencyFactory
import javax.inject.Inject

interface DocSourceDirectorySet : SourceDirectorySet

abstract class DefaultDocSourceDirectorySet
    @Inject
    constructor(
        sourceDirectorySet: SourceDirectorySet,
        taskDependencyFactory: TaskDependencyFactory,
    ) : DefaultSourceDirectorySet(sourceDirectorySet, taskDependencyFactory),
        DocSourceDirectorySet
