package top.lanshan.manmu.search;

import top.lanshan.manmu.model.SiteInformation;

import java.util.List;

public interface WebSearchClient {

	List<SiteInformation> search(String query);

}
