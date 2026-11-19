package io.github.exday.api.projectlist;

/** 企画の一覧のカーソルが、渡された絞り込み・並べ方で作ったものでない、または並びのキーの企画がない（400）。 */
public class InvalidProjectListCursorException extends RuntimeException {

    public InvalidProjectListCursorException() {
        super("project list cursor does not match the query");
    }
}
