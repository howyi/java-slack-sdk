package com.github.seratch.jslack.api.scim.request;

import com.github.seratch.jslack.api.methods.SlackApiRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupsSearchRequest implements SlackApiRequest {
    private String token;
    // https://api.slack.com/scim#filter
    private String filter;
    // https://api.slack.com/changelog/2019-06-have-scim-will-paginate
    private Integer count;
    private Integer startIndex;
}
