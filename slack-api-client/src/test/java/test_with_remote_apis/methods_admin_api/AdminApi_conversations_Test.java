package test_with_remote_apis.methods_admin_api;

import com.slack.api.Slack;
import com.slack.api.methods.AsyncMethodsClient;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.admin.conversations.AdminConversationsSetConversationPrefsRequest;
import com.slack.api.methods.response.admin.conversations.*;
import com.slack.api.methods.response.admin.conversations.ekm.AdminConversationsEkmListOriginalConnectedChannelInfoResponse;
import com.slack.api.methods.response.admin.conversations.restrict_access.AdminConversationsRestrictAccessAddGroupResponse;
import com.slack.api.methods.response.admin.conversations.restrict_access.AdminConversationsRestrictAccessListGroupsResponse;
import com.slack.api.methods.response.admin.conversations.restrict_access.AdminConversationsRestrictAccessRemoveGroupResponse;
import com.slack.api.methods.response.admin.conversations.whitelist.AdminConversationsWhitelistAddResponse;
import com.slack.api.methods.response.admin.conversations.whitelist.AdminConversationsWhitelistListGroupsLinkedToChannelResponse;
import com.slack.api.methods.response.admin.conversations.whitelist.AdminConversationsWhitelistRemoveResponse;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.User;
import config.Constants;
import config.SlackTestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
public class AdminApi_conversations_Test {

    static SlackTestConfig testConfig = SlackTestConfig.getInstance();
    static Slack slack = Slack.getInstance(testConfig.getConfig());

    @AfterClass
    public static void tearDown() throws InterruptedException {
        SlackTestConfig.awaitCompletion(testConfig);
    }

    static String teamAdminUserToken = System.getenv(Constants.SLACK_SDK_TEST_GRID_WORKSPACE_ADMIN_USER_TOKEN);
    static String orgAdminUserToken = System.getenv(Constants.SLACK_SDK_TEST_GRID_ORG_ADMIN_USER_TOKEN);
    static String teamId = System.getenv(Constants.SLACK_SDK_TEST_GRID_TEAM_ID);
    static String teamId2 = System.getenv(Constants.SLACK_SDK_TEST_GRID_TEAM_ID_2);
    static String idpUsergroupId = System.getenv(Constants.SLACK_SDK_TEST_GRID_IDP_USERGROUP_ID);
    static String sharedChannelId = System.getenv(Constants.SLACK_SDK_TEST_GRID_SHARED_CHANNEL_ID);

    static AsyncMethodsClient methodsAsync = slack.methodsAsync(orgAdminUserToken);

    @Test
    public void search() throws Exception {
        if (orgAdminUserToken != null) {
            AdminConversationsSearchResponse search = methodsAsync.adminConversationsSearch(r -> r
                    .limit(1)
                    .sort("member_count")
                    .sortDir("desc")
                    .searchChannelTypes(Arrays.asList("exclude_archived", "private_exclude"))
            ).get();
            assertThat(search.getError(), is(nullValue()));
            assertThat(search.getConversations().size(), is(1));

            AdminConversationsSearchResponse search2 = methodsAsync.adminConversationsSearch(r -> r
                    .limit(1)
                    .sort("member_count")
                    .sortDir("desc")
                    .searchChannelTypes(Arrays.asList("exclude_archived", "private_exclude"))
                    .cursor(search.getNextCursor())
            ).get();
            assertThat(search2.getError(), is(nullValue()));
            assertThat(search2.getConversations().size(), is(1));
        }
    }

    @Test
    public void basicOperations() throws Exception {
        if (teamAdminUserToken != null && orgAdminUserToken != null) {
            String channelName = "test-" + System.currentTimeMillis();
            AdminConversationsCreateResponse creation = methodsAsync.adminConversationsCreate(r -> r
                    .isPrivate(false)
                    .name(channelName)
                    .teamId(teamId)
                    .orgWide(false)
                    .description("This is a test channel")
            ).get();

            assertThat(creation.getError(), is(nullValue()));
            String channelId = creation.getChannelId();
            String userId = findUserId(Arrays.asList("USLACKBOT"));
            AdminConversationsInviteResponse invitation =
                    methodsAsync.adminConversationsInvite(r -> r.channelId(channelId).userIds(Arrays.asList("USLACKBOT"))).get();
            assertThat(invitation.getError(), is("failed_for_some_users"));

            invitation = methodsAsync.adminConversationsInvite(r -> r.channelId(channelId).userIds(Arrays.asList(userId))).get();
            assertThat(invitation.getError(), is(nullValue()));

            AdminConversationsArchiveResponse archived =
                    methodsAsync.adminConversationsArchive(r -> r.channelId(channelId)).get();
            assertThat(archived.getError(), is(nullValue()));

            AdminConversationsUnarchiveResponse unarchived =
                    methodsAsync.adminConversationsUnarchive(r -> r.channelId(channelId)).get();
            assertThat(unarchived.getError(), is(nullValue()));

            AdminConversationsRenameResponse renamed =
                    methodsAsync.adminConversationsRename(r -> r.channelId(channelId).name(channelName + "-renamed")).get();
            assertThat(renamed.getError(), is(nullValue()));

            AdminConversationsGetConversationPrefsResponse prefs =
                    methodsAsync.adminConversationsGetConversationPrefs(r -> r.channelId(channelId)).get();
            assertThat(prefs.getError(), is(nullValue()));

            AdminConversationsSetConversationPrefsRequest.Prefs newPrefs =
                    new AdminConversationsSetConversationPrefsRequest.Prefs();
            newPrefs.getWhoCanPost().setTypes(Arrays.asList("owner", "admin"));
            newPrefs.getWhoCanPost().setUsers(Arrays.asList(userId));
            newPrefs.getCanThread().setTypes(Arrays.asList("admin", "org_admin"));
            newPrefs.getCanThread().setUsers(Arrays.asList(userId));
            AdminConversationsSetConversationPrefsResponse prefsUpdate =
                    methodsAsync.adminConversationsSetConversationPrefs(r -> r.channelId(channelId).prefs(newPrefs)).get();
            assertThat(prefsUpdate.getError(), is(nullValue()));

            prefs = methodsAsync.adminConversationsGetConversationPrefs(r -> r.channelId(channelId)).get();
            assertThat(prefs.getError(), is(nullValue()));
            assertThat(prefs.getPrefs().getWhoCanPost().getType(), is(Arrays.asList("owner", "admin")));
            assertThat(prefs.getPrefs().getWhoCanPost().getUser(), is(Arrays.asList(userId)));
            assertThat(prefs.getPrefs().getCanThread().getType(), is(Arrays.asList("admin", "org_admin")));
            assertThat(prefs.getPrefs().getCanThread().getUser(), is(Arrays.asList(userId)));

            String prefAsString = "{\"who_can_post\":\"type:owner,type:admin,user:" + userId + "\"," +
                    "\"can_thread\":\"type:admin,type:org_admin,user:" + userId + "\"}";
            prefsUpdate = methodsAsync.adminConversationsSetConversationPrefs(r -> r
                    .channelId(channelId).prefsAsString(prefAsString)).get();
            assertThat(prefsUpdate.getError(), is(nullValue()));

            prefs = methodsAsync.adminConversationsGetConversationPrefs(r -> r.channelId(channelId)).get();
            assertThat(prefs.getError(), is(nullValue()));
            assertThat(prefs.getPrefs().getWhoCanPost().getType(), is(Arrays.asList("owner", "admin")));
            assertThat(prefs.getPrefs().getWhoCanPost().getUser(), is(Arrays.asList(userId)));
            assertThat(prefs.getPrefs().getCanThread().getType(), is(Arrays.asList("admin", "org_admin")));
            assertThat(prefs.getPrefs().getCanThread().getUser(), is(Arrays.asList(userId)));


            AdminConversationsGetTeamsResponse teams =
                    methodsAsync.adminConversationsGetTeams(r -> r.channelId(channelId).limit(10)).get();
            assertThat(teams.getError(), is(nullValue()));

            AdminConversationsSetTeamsResponse setTeams =
                    methodsAsync.adminConversationsSetTeams(r -> r
                            .channelId(channelId)
                            .teamId(teamId)
                            .orgChannel(true)).get();
            assertThat(setTeams.getError(), is(nullValue()));

            AdminConversationsLookupResponse lookup = methodsAsync.adminConversationsLookup(r -> r
                    .teamIds(Arrays.asList(teamId))
                    .lastMessageActivityBefore(100)
            ).get();
            assertThat(lookup.getError(), is(nullValue()));

            AdminConversationsDisconnectSharedResponse disconnectShared =
                    methodsAsync.adminConversationsDisconnectShared(r -> r
                            .channelId(channelId).leavingTeamIds(Arrays.asList(teamId))).get();
            // TODO: Fix this
            // the error can be either not_supported or channel_not_found
            assertThat(disconnectShared.getError(), is(notNullValue()));

            Thread.sleep(2000L); // To avoid channel_not_found

            AdminConversationsConvertToPrivateResponse conversion =
                    methodsAsync.adminConversationsConvertToPrivate(r -> r.channelId(channelId)).get();
            assertThat(conversion.getError(), is(nullValue()));

            Thread.sleep(2000L); // To avoid internal_error

            AdminConversationsConvertToPublicResponse reConversion =
                    methodsAsync.adminConversationsConvertToPublic(r -> r.channelId("CCC")).get();
            assertThat(reConversion.getError(), is("invalid_arguments"));
            assertThat(reConversion.getResponseMetadata().getMessages().get(0),
                    is(startsWith("[ERROR] input must match regex pattern:")));
            reConversion = methodsAsync.adminConversationsConvertToPublic(r -> r.channelId(channelId)).get();
            assertThat(reConversion.getError(), is(nullValue()));

            Thread.sleep(2000L); // To avoid internal_error

            archived = methodsAsync.adminConversationsArchive(r -> r.channelId(channelId)).get();
            assertThat(archived.getError(), is(nullValue()));

            Thread.sleep(2000L); // To avoid internal_error

            AdminConversationsDeleteResponse deletion = methodsAsync.adminConversationsDelete(r -> r
                    .channelId(channelId)).get();
            assertThat(deletion.getError(), is(nullValue()));
        }
    }

    @Test
    public void ekm() throws Exception {
        if (orgAdminUserToken != null) {
            AdminConversationsEkmListOriginalConnectedChannelInfoResponse result = methodsAsync.adminConversationsEkmListOriginalConnectedChannelInfo(r -> r
                    .channelIds(Arrays.asList(sharedChannelId))
                    .teamIds(Arrays.asList(teamId))
            ).get();
            // TODO: Enable and run valid tests
            assertThat(result.getError(), is("not_enabled"));
        }
    }

    @Ignore // TODO: Fix this test to be more stable
    @Test
    public void changeSharedChannels() throws Exception {
        if (teamAdminUserToken != null && orgAdminUserToken != null && sharedChannelId != null) {
            ConversationsInfoResponse channelInfo = slack.methods(teamAdminUserToken).conversationsInfo(r -> r.channel(sharedChannelId));
            assertThat(channelInfo.getError(), is(nullValue()));
            Conversation channel = channelInfo.getChannel();
            String originalTeamId = channel.getSharedTeamIds().get(0);
            String channelId = channel.getId();

            List<String> newTeams = channel.getSharedTeamIds().stream()
                    .limit(channel.getSharedTeamIds().size() - 1)
                    .collect(Collectors.toList());

            AdminConversationsSetTeamsResponse shareResp = methodsAsync.adminConversationsSetTeams(r -> r
                            .teamId(originalTeamId)
                            .channelId(channelId)
                            .targetTeamIds(newTeams))
                    .get();
            assertThat(shareResp.getError(), is(nullValue()));

            AdminConversationsSetTeamsResponse revertResp = methodsAsync.adminConversationsSetTeams(r -> r
                            .teamId(originalTeamId)
                            .channelId(channelId)
                            .targetTeamIds(channel.getSharedTeamIds()))
                    .get();
            assertThat(revertResp.getError(), is(nullValue()));
        }
    }

    static String getOrCreatePrivateChannel() throws Exception {
        List<Conversation> privateChannels = slack.methods(teamAdminUserToken).conversationsList(r -> r
                .excludeArchived(true).types(Arrays.asList(ConversationType.PRIVATE_CHANNEL)).limit(1)).getChannels();
        if (privateChannels.size() == 0 || privateChannels.get(0).isShared()) {
            String name = "private-test-" + System.currentTimeMillis();
            ConversationsCreateResponse creation = slack.methods(teamAdminUserToken).conversationsCreate(r -> r.name(name).isPrivate(true));
            return creation.getChannel().getId();
        } else {
            return privateChannels.get(0).getId();
        }
    }

    @Test
    public void customRetention() throws Exception {
        if (orgAdminUserToken != null) {
            String channelId = getOrCreatePrivateChannel();
            MethodsClient client = slack.methods(orgAdminUserToken);
            try {
                AdminConversationsGetCustomRetentionResponse get =
                        client.adminConversationsGetCustomRetention(r -> r.channelId(channelId));
                assertThat(get.getError(), is(nullValue()));
                assertThat(get.isPolicyEnabled(), is(false));
                assertThat(get.getDurationDays(), is(0));

                AdminConversationsSetCustomRetentionResponse set =
                        client.adminConversationsSetCustomRetention(r -> r.channelId(channelId).durationDays(365));
                assertThat(set.getError(), is(nullValue()));

                get = client.adminConversationsGetCustomRetention(r -> r.channelId(channelId));
                assertThat(get.getError(), is(nullValue()));
                assertThat(get.isPolicyEnabled(), is(true));
                assertThat(get.getDurationDays(), is(365));

                AdminConversationsRemoveCustomRetentionResponse remove =
                        client.adminConversationsRemoveCustomRetention(r -> r.channelId(channelId));
                assertThat(set.getError(), is(nullValue()));

                get = client.adminConversationsGetCustomRetention(r -> r.channelId(channelId));
                assertThat(get.isPolicyEnabled(), is(false));
                assertThat(get.getDurationDays(), is(0));

            } finally {
                client.adminConversationsDelete(r -> r.channelId(channelId));
            }
        }
    }

    @Test
    @Ignore
    public void restrictAccess() throws Exception {
        if (teamAdminUserToken != null && orgAdminUserToken != null && idpUsergroupId != null) {
            String channelId = getOrCreatePrivateChannel();
            MethodsClient orgAdminClient = slack.methods(orgAdminUserToken);
            AdminConversationsRestrictAccessListGroupsResponse list =
                    orgAdminClient.adminConversationsRestrictAccessListGroups(r -> r
                            .channelId(channelId).teamId(teamId));
            assertThat(list.getError(), is(nullValue()));

            Thread.sleep(10000L); // TO avoid rate limited errors
            list = orgAdminClient.adminConversationsRestrictAccessListGroups(r -> r
                    .channelId("dummy").teamId(teamId));
            assertThat(list.getError(), is("invalid_arguments"));

            AdminConversationsRestrictAccessAddGroupResponse add =
                    orgAdminClient.adminConversationsRestrictAccessAddGroup(r -> r
                            .teamId(teamId).channelId(channelId).groupId(idpUsergroupId));
            assertThat(add.getError(), is(nullValue()));

            Thread.sleep(10000L); // TO avoid rate limited errors
            add = orgAdminClient.adminConversationsRestrictAccessAddGroup(r -> r
                    .teamId(teamId).channelId("dummy").groupId(idpUsergroupId));
            assertThat(add.getError(), is("invalid_arguments"));

            AdminConversationsRestrictAccessRemoveGroupResponse remove =
                    orgAdminClient.adminConversationsRestrictAccessRemoveGroup(r -> r
                            .teamId(teamId).channelId(channelId).groupId(idpUsergroupId));
            // TODO: "link_not_found" can arise - 2022-12
            assertThat(remove.getError(), is(nullValue()));

            Thread.sleep(20000L); // TO avoid rate limited errors
            remove = orgAdminClient.adminConversationsRestrictAccessRemoveGroup(r -> r
                    .teamId(teamId).channelId("dummy").groupId(idpUsergroupId));
            assertThat(remove.getError(), is("invalid_arguments"));
        }
    }

    @Ignore
    @Test
    public void whitelist() throws Exception {
        if (teamAdminUserToken != null && orgAdminUserToken != null && idpUsergroupId != null) {
            String channelId = getOrCreatePrivateChannel();
            MethodsClient orgAdminClient = slack.methods(orgAdminUserToken);
            AdminConversationsWhitelistListGroupsLinkedToChannelResponse list =
                    orgAdminClient.adminConversationsWhitelistListGroupsLinkedToChannel(r -> r.channelId(channelId).teamId(teamId));
            assertThat(list.getError(), is(nullValue()));

            Thread.sleep(10000L); // TO avoid rate limited errors
            list = orgAdminClient.adminConversationsWhitelistListGroupsLinkedToChannel(r -> r.channelId("dummy").teamId(teamId));
            assertThat(list.getError(), is("invalid_arguments"));

            AdminConversationsWhitelistAddResponse add = orgAdminClient.adminConversationsWhitelistAdd(r -> r
                    .teamId(teamId).channelId(channelId).groupId(idpUsergroupId));
            assertThat(add.getError(), is(nullValue()));

            Thread.sleep(10000L); // TO avoid rate limited errors
            add = orgAdminClient.adminConversationsWhitelistAdd(r -> r
                    .teamId(teamId).channelId("dummy").groupId(idpUsergroupId));
            assertThat(add.getError(), is("invalid_arguments"));

            AdminConversationsWhitelistRemoveResponse remove = orgAdminClient.adminConversationsWhitelistRemove(r -> r
                    .teamId(teamId).channelId(channelId).groupId(idpUsergroupId));
            assertThat(remove.getError(), is(nullValue()));

            Thread.sleep(20000L); // TO avoid rate limited errors
            remove = orgAdminClient.adminConversationsWhitelistRemove(r -> r
                    .teamId(teamId).channelId("dummy").groupId(idpUsergroupId));
            assertThat(remove.getError(), is("invalid_arguments"));
        }
    }

    @Test
    public void adminConversationsBulkMove() throws Exception {
        if (orgAdminUserToken != null) {
            AsyncMethodsClient orgAdminClient = slack.methodsAsync(orgAdminUserToken);
            AdminConversationsCreateResponse creation1 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-1-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation1.getError(), is(nullValue()));
            AdminConversationsCreateResponse creation2 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-2-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation2.getError(), is(nullValue()));

            AdminConversationsBulkMoveResponse moving = null;
            while (moving == null || (moving.getError() != null && moving.getError().equals("action_already_in_progress"))) {
                moving = orgAdminClient.adminConversationsBulkMove(r -> r
                        .channelIds(Arrays.asList(creation1.getChannelId(), creation2.getChannelId()))
                        .targetTeamId(teamId2)
                ).get();
                if (moving.getError() != null) {
                    Thread.sleep(3_000L);
                }
            }
            // To receive response_metadata
            moving = orgAdminClient.adminConversationsBulkMove(r -> r
                    .channelIds(Arrays.asList("dummy"))
                    .targetTeamId(teamId2)
            ).get();
            assertThat(moving.getError(), is("invalid_arguments"));
        }
    }

    @Test
    public void adminConversationsBulkArchive() throws Exception {
        if (orgAdminUserToken != null) {
            AsyncMethodsClient orgAdminClient = slack.methodsAsync(orgAdminUserToken);
            AdminConversationsCreateResponse creation1 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-1-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation1.getError(), is(nullValue()));
            AdminConversationsCreateResponse creation2 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-2-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation2.getError(), is(nullValue()));

            AdminConversationsBulkArchiveResponse archiving = null;
            while (archiving == null || (archiving.getError() != null && archiving.getError().equals("action_already_in_progress"))) {
                archiving = orgAdminClient.adminConversationsBulkArchive(r -> r
                        .channelIds(Arrays.asList(creation1.getChannelId(), creation2.getChannelId()))
                ).get();
                if (archiving.getError() != null) {
                    Thread.sleep(3_000L);
                }
            }
            // To receive response_metadata
            archiving = orgAdminClient.adminConversationsBulkArchive(r -> r
                    .channelIds(Arrays.asList("dummy"))
            ).get();
            assertThat(archiving.getError(), is("invalid_arguments"));
        }
    }

    @Test
    public void adminConversationsBulkDelete() throws Exception {
        if (orgAdminUserToken != null) {
            AsyncMethodsClient orgAdminClient = slack.methodsAsync(orgAdminUserToken);
            AdminConversationsCreateResponse creation1 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-1-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation1.getError(), is(nullValue()));
            AdminConversationsCreateResponse creation2 = orgAdminClient.adminConversationsCreate(r -> r
                    .name("test-channel-2-" + System.currentTimeMillis())
                    .teamId(teamId)
                    .isPrivate(false)
            ).get();
            assertThat(creation2.getError(), is(nullValue()));

            AdminConversationsBulkDeleteResponse deletion = null;
            while (deletion == null || (deletion.getError() != null && deletion.getError().equals("action_already_in_progress"))) {
                deletion = orgAdminClient.adminConversationsBulkDelete(r -> r
                        .channelIds(Arrays.asList(creation1.getChannelId(), creation2.getChannelId()))
                ).get();
                if (deletion.getError() != null) {
                    Thread.sleep(3_000L);
                }
            }
            // To receive response_metadata
            deletion = orgAdminClient.adminConversationsBulkDelete(r -> r
                    .channelIds(Arrays.asList("dummy"))
            ).get();
            assertThat(deletion.getError(), is("invalid_arguments"));
        }
    }

    private String findUserId(List<String> idsToSkip) throws Exception {
        String userId = null;
        UsersListResponse usersListResponse = slack.methodsAsync(teamAdminUserToken).usersList(req -> req).get();
        assertThat(usersListResponse.getError(), is(nullValue()));
        List<User> members = usersListResponse.getMembers();
        for (User member : members) {
            if (member.isBot() || member.isDeleted() || member.isAppUser() || member.isOwner() || member.isStranger()) {
                continue;
            } else {
                if (!idsToSkip.contains(member.getId())) {
                    userId = member.getId();
                    break;
                }
            }
        }
        return userId;
    }

}
