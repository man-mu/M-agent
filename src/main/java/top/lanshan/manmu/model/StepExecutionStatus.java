package top.lanshan.manmu.model;

public final class StepExecutionStatus {

	private static final String ASSIGNED_PREFIX = "assigned_";

	private static final String PROCESSING_PREFIX = "processing_";

	private static final String COMPLETED_PREFIX = "completed_";

	private static final String ERROR_PREFIX = "error_";

	private StepExecutionStatus() {
	}

	public static String pending() {
		return ResearchStep.STATUS_PENDING;
	}

	public static String assigned(String nodeName) {
		return ASSIGNED_PREFIX + requireNodeName(nodeName);
	}

	public static String processing(String nodeName) {
		return PROCESSING_PREFIX + requireNodeName(nodeName);
	}

	public static String completed(String nodeName) {
		return COMPLETED_PREFIX + requireNodeName(nodeName);
	}

	public static String error(String nodeName) {
		return ERROR_PREFIX + requireNodeName(nodeName);
	}

	public static String legacyError(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return ResearchStep.STATUS_ERROR;
		}
		return ResearchStep.STATUS_ERROR + ": " + errorMessage;
	}

	public static boolean isTerminal(ResearchStep step) {
		return step != null && isTerminal(step.executionStatus());
	}

	public static boolean isTerminal(String status) {
		return isCompleted(status) || isError(status);
	}

	public static boolean isCompleted(ResearchStep step) {
		return step != null && isCompleted(step.executionStatus());
	}

	public static boolean isCompleted(String status) {
		String normalized = normalize(status);
		return ResearchStep.STATUS_COMPLETED.equals(normalized) || normalized.startsWith(COMPLETED_PREFIX);
	}

	public static boolean isError(ResearchStep step) {
		return step != null && isError(step.executionStatus());
	}

	public static boolean isError(String status) {
		String normalized = normalize(status);
		return ResearchStep.STATUS_ERROR.equals(normalized) || normalized.startsWith(ERROR_PREFIX)
				|| normalized.startsWith(ResearchStep.STATUS_ERROR + ":");
	}

	public static boolean isAssigned(String status) {
		return normalize(status).startsWith(ASSIGNED_PREFIX);
	}

	public static boolean isAssignedTo(String status, String nodeName) {
		if (nodeName == null || nodeName.isBlank()) {
			return false;
		}
		return normalize(status).equals(ASSIGNED_PREFIX + nodeName.strip());
	}

	public static boolean isProcessing(String status) {
		String normalized = normalize(status);
		return ResearchStep.STATUS_PROCESSING.equals(normalized) || normalized.startsWith(PROCESSING_PREFIX);
	}

	public static boolean isRunning(String status) {
		return isAssigned(status) || isProcessing(status);
	}

	public static String normalize(String status) {
		return status == null || status.isBlank() ? ResearchStep.STATUS_PENDING : status.strip();
	}

	private static String requireNodeName(String nodeName) {
		if (nodeName == null || nodeName.isBlank()) {
			throw new IllegalArgumentException("nodeName must not be blank");
		}
		return nodeName.strip();
	}

}
