package io.github.exday.api.project;

/** 企画がない・見られない（非公開）、またはカーソルがその企画のものでない。 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String projectId) {
        super("project not found: " + projectId);
    }
}
