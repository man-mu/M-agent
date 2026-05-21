package top.lanshan.manmu.model;

public record SiteInformation(String title, String url, String snippet, String summary, String siteName,
		String siteIcon, String datePublished) {

	public String promptText() {
		StringBuilder text = new StringBuilder();
		appendLine(text, "Title", title);
		appendLine(text, "URL", url);
		appendLine(text, "Site", siteName);
		appendLine(text, "Published", datePublished);
		appendLine(text, "Snippet", snippet);
		appendLine(text, "Summary", summary);
		return text.toString().strip();
	}

	private void appendLine(StringBuilder text, String label, String value) {
		if (value != null && !value.isBlank()) {
			text.append(label).append(": ").append(value.strip()).append('\n');
		}
	}

}
