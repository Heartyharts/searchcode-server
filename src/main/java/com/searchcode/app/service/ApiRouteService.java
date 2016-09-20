/*
 * Copyright (c) 2016 Boyter Online Services
 *
 * Use of this software is governed by the Fair Source License included
 * in the LICENSE.TXT file, but will be eventually open under GNU General Public License Version 3
 * see the README.md for when this clause will take effect
 *
 * Version 1.4.0
 */

package com.searchcode.app.service;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.searchcode.app.config.InjectorConfig;
import com.searchcode.app.config.Values;
import com.searchcode.app.dao.Data;
import com.searchcode.app.dto.CodeResult;
import com.searchcode.app.dto.SearchResult;
import com.searchcode.app.util.SearchcodeLib;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParser;
import spark.Request;
import spark.Response;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains API Route logic
 */
public class ApiRouteService {

    private final Injector injector;

    public ApiRouteService() {
        injector = Guice.createInjector(new InjectorConfig());
    }

    public SearchResult getTimeSearch(Request request, Response response) {
        Data data = injector.getInstance(Data.class);

        SearchcodeLib scl = Singleton.getSearchcodeLib(data);
        TimeCodeSearcher cs = new TimeCodeSearcher();
        CodeMatcher cm = new CodeMatcher(data);

        response.header("Content-Encoding", "gzip");

        if (request.queryParams().contains("q") == false || Values.EMPTYSTRING.equals(request.queryParams("q").trim())) {
            return null;
        }

        String query = request.queryParams("q").trim();

        int page = this.getPage(request);

        String[] repos;
        String[] langs;
        String[] owners;
        String reposFilter = "";
        String langsFilter = "";
        String ownersFilter = "";

        if(request.queryParams().contains("repo")) {
            repos = request.queryParamsValues("repo");
            reposFilter = getRepos(repos, reposFilter);
        }

        if(request.queryParams().contains("lan")) {
            langs = request.queryParamsValues("lan");
            langsFilter = getLanguages(langs, langsFilter);
        }

        if(request.queryParams().contains("own")) {
            owners = request.queryParamsValues("own");
            ownersFilter = getOwners(owners, ownersFilter);
        }

        // split the query escape it and and it together
        String cleanQueryString = scl.formatQueryString(query);

        SearchResult searchResult = cs.search(cleanQueryString + reposFilter + langsFilter + ownersFilter, page);
        searchResult.setCodeResultList(cm.formatResults(searchResult.getCodeResultList(), query, true));

        searchResult.setQuery(query);

        this.getAltQueries(scl, query, searchResult);

        // Null out code as it isn't required and there is no point in bloating our ajax requests
        for(CodeResult codeSearchResult: searchResult.getCodeResultList()) {
            codeSearchResult.setCode(null);
        }

        return searchResult;
    }

    private int getPage(Request request) {
        int page = 0;

        if(request.queryParams().contains("p")) {
            try {
                page = Integer.parseInt(request.queryParams("p"));
                page = page > 19 ? 19 : page;
            }
            catch(NumberFormatException ex) {
                page = 0;
            }
        }
        return page;
    }

    private void getAltQueries(SearchcodeLib scl, String query, SearchResult searchResult) {
        for(String altQuery: scl.generateAltQueries(query)) {
            searchResult.addAltQuery(altQuery);
        }
    }

    private String getOwners(String[] owners, String ownersFilter) {
        if (owners.length != 0) {
            List<String> ownersList = Arrays.asList(owners).stream()
                    .map((s) -> "codeowner:" + QueryParser.escape(s))
                    .collect(Collectors.toList());

            ownersFilter = " && (" + StringUtils.join(ownersList, " || ") + ")";
        }
        return ownersFilter;
    }

    private String getLanguages(String[] langs, String langsFilter) {
        if (langs.length != 0) {
            List<String> langsList = Arrays.asList(langs).stream()
                    .map((s) -> "languagename:" + QueryParser.escape(s))
                    .collect(Collectors.toList());

            langsFilter = " && (" + StringUtils.join(langsList, " || ") + ")";
        }
        return langsFilter;
    }

    private String getRepos(String[] repos, String reposFilter) {
        if (repos.length != 0) {
            List<String> reposList = Arrays.asList(repos).stream()
                    .map((s) -> "reponame:" + QueryParser.escape(s))
                    .collect(Collectors.toList());

            reposFilter = " && (" + StringUtils.join(reposList, " || ") + ")";
        }
        return reposFilter;
    }
}
