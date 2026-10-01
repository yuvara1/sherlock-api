package in.sherlock.project.domain;

public class ProjectException extends RuntimeException {
    private final int status;
    private final String code;

    public ProjectException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
    public static ProjectException missing() { return new ProjectException(404, "NOT_FOUND", "Resource not found."); }
    public static ProjectException forbidden() { return new ProjectException(403, "FORBIDDEN", "You do not have permission to perform this action."); }
    public static ProjectException invalid(String message) { return new ProjectException(400, "VALIDATION_ERROR", message); }
}
