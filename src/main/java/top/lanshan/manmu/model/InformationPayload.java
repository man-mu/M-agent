package top.lanshan.manmu.model;

import java.util.List;

public record InformationPayload(String query, List<SiteInformation> siteInformation) {

	public InformationPayload {
		siteInformation = siteInformation == null ? List.of() : List.copyOf(siteInformation);
	}

}
