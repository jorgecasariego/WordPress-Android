package org.wordpress.android.ui.stats.refresh.lists.sections.insights.usecases

import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import org.wordpress.android.R
import org.wordpress.android.R.string
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.FollowersModel
import org.wordpress.android.fluxc.model.stats.FollowersModel.FollowerModel
import org.wordpress.android.fluxc.store.InsightsStore
import org.wordpress.android.fluxc.store.StatsStore.InsightsTypes.FOLLOWERS
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.stats.StatsUtilsWrapper
import org.wordpress.android.ui.stats.refresh.lists.NavigationTarget.ViewFollowersStats
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.StatefulUseCase
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Empty
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Information
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Label
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Link
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.NavigationAction
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.TabsItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Title
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.UserItem
import org.wordpress.android.viewmodel.ResourceProvider
import javax.inject.Inject
import javax.inject.Named

private const val PAGE_SIZE = 6

class FollowersUseCase
@Inject constructor(
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val insightsStore: InsightsStore,
    private val statsUtilsWrapper: StatsUtilsWrapper,
    private val resourceProvider: ResourceProvider
) : StatefulUseCase<Pair<FollowersModel, FollowersModel>, Int>(
        FOLLOWERS,
        mainDispatcher,
        0
) {
    override suspend fun loadCachedData(site: SiteModel) {
        val wpComFollowers = insightsStore.getWpComFollowers(site, PAGE_SIZE)
        val emailFollowers = insightsStore.getEmailFollowers(site, PAGE_SIZE)
        if (wpComFollowers != null && emailFollowers != null) {
            onModel(wpComFollowers to emailFollowers)
        }
    }

    override suspend fun fetchRemoteData(site: SiteModel, forced: Boolean) {
        val deferredWpComResponse = GlobalScope.async { insightsStore.fetchWpComFollowers(site, PAGE_SIZE, forced) }
        val deferredEmailResponse = GlobalScope.async { insightsStore.fetchEmailFollowers(site, PAGE_SIZE, forced) }
        val wpComResponse = deferredWpComResponse.await()
        val emailResponse = deferredEmailResponse.await()
        val wpComModel = wpComResponse.model
        val emailModel = emailResponse.model
        val error = wpComResponse.error ?: emailResponse.error

        when {
            error != null -> onError(error.message ?: error.type.name)
            wpComModel != null && emailModel != null -> onModel(wpComModel to emailModel)
            else -> {
                onModel(null)
            }
        }
    }

    override fun buildStatefulUiModel(
        model: Pair<FollowersModel, FollowersModel>,
        uiState: Int
    ): List<BlockListItem> {
        val wpComModel = model.first
        val emailModel = model.second
        val items = mutableListOf<BlockListItem>()
        items.add(Title(string.stats_view_followers))
        items.add(
                TabsItem(
                        listOf(
                                R.string.stats_followers_wordpress_com,
                                R.string.stats_followers_email
                        ),
                        uiState
                ) {
                    onUiState(it)
                }
        )
        if (uiState == 0) {
            items.addAll(buildTab(wpComModel, R.string.stats_followers_wordpress_com))
        } else {
            items.addAll(buildTab(emailModel, R.string.stats_followers_email))
        }

        if (wpComModel.hasMore || emailModel.hasMore) {
            items.add(
                    Link(
                            text = string.stats_insights_view_more,
                            navigationAction = NavigationAction(ViewFollowersStats, mutableNavigationTarget)
                    )
            )
        }
        return items
    }

    private fun buildTab(model: FollowersModel, label: Int): List<BlockListItem> {
        val mutableItems = mutableListOf<BlockListItem>()
        if (model.followers.isNotEmpty()) {
            mutableItems.add(
                    Information(
                            resourceProvider.getString(
                                    string.stats_followers_count_message,
                                    resourceProvider.getString(label),
                                    model.totalCount
                            )
                    )
            )
            mutableItems.add(Label(R.string.stats_follower_label, R.string.stats_follower_since_label))
            model.followers.toUserItems().let { mutableItems.addAll(it) }
        } else {
            mutableItems.add(Empty)
        }
        return mutableItems
    }

    private fun List<FollowerModel>.toUserItems(): List<UserItem> {
        return this.mapIndexed { index, follower ->
            UserItem(
                    follower.avatar,
                    follower.label,
                    statsUtilsWrapper.getSinceLabelLowerCase(follower.dateSubscribed),
                    index < this.size - 1
            )
        }
    }
}
