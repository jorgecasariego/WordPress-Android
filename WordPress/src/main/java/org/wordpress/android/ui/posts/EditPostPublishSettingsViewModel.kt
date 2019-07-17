package org.wordpress.android.ui.posts

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.wordpress.android.R
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.fluxc.model.post.PostStatus.DRAFT
import org.wordpress.android.fluxc.model.post.PostStatus.PUBLISHED
import org.wordpress.android.fluxc.model.post.PostStatus.SCHEDULED
import org.wordpress.android.ui.posts.PostNotificationTimeDialogFragment.NotificationTime
import org.wordpress.android.ui.posts.PostNotificationTimeDialogFragment.NotificationTime.OFF
import org.wordpress.android.util.DateTimeUtils
import org.wordpress.android.util.LocaleManagerWrapper
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ResourceProvider
import java.util.Calendar
import javax.inject.Inject

class EditPostPublishSettingsViewModel
@Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val postSettingsUtils: PostSettingsUtils,
    private val localeManagerWrapper: LocaleManagerWrapper
) : ViewModel() {
    var canPublishImmediately: Boolean = false

    var year: Int? = null
        private set
    var month: Int? = null
        private set
    var day: Int? = null
        private set
    var hour: Int? = null
        private set
    var minute: Int? = null
        private set

    private val _onDatePicked = MutableLiveData<Event<Unit>>()
    val onDatePicked: LiveData<Event<Unit>> = _onDatePicked
    private val _onPublishedDateChanged = MutableLiveData<Calendar>()
    val onPublishedDateChanged: LiveData<Calendar> = _onPublishedDateChanged
    private val _onPostStatusChanged = MutableLiveData<PostStatus>()
    val onPostStatusChanged: LiveData<PostStatus> = _onPostStatusChanged
    private val _onUiModel = MutableLiveData<PublishUiModel>()
    val onUiModel: LiveData<PublishUiModel> = _onUiModel
    private val _onToast = MutableLiveData<Event<String>>()
    val onToast: LiveData<Event<String>> = _onToast
    private val _onNotificationTime = MutableLiveData<NotificationTime>()
    val onNotificationTime: LiveData<NotificationTime> = _onNotificationTime

    fun start(postModel: PostModel?) {
        val startCalendar = postModel?.let { getCurrentPublishDateAsCalendar(it) }
                ?: localeManagerWrapper.getCurrentCalendar()
        updateUiModel(post = postModel)
        year = startCalendar.get(Calendar.YEAR)
        month = startCalendar.get(Calendar.MONTH)
        day = startCalendar.get(Calendar.DAY_OF_MONTH)
        hour = startCalendar.get(Calendar.HOUR_OF_DAY)
        minute = startCalendar.get(Calendar.MINUTE)
        onPostStatusChanged(postModel)
    }

    fun onPostStatusChanged(postModel: PostModel?) {
        canPublishImmediately = postModel?.let { PostUtils.shouldPublishImmediatelyOptionBeAvailable(it) } ?: false
        updateUiModel(post = postModel)
    }

    fun publishNow() {
        _onPublishedDateChanged.postValue(localeManagerWrapper.getCurrentCalendar())
    }

    fun onTimeSelected(selectedHour: Int, selectedMinute: Int) {
        this.hour = selectedHour
        this.minute = selectedMinute
        val calendar = localeManagerWrapper.getCurrentCalendar()
        calendar.set(year!!, month!!, day!!, hour!!, minute!!)
        _onPublishedDateChanged.postValue(calendar)
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        this.year = year
        this.month = month
        this.day = dayOfMonth
        _onDatePicked.postValue(Event(Unit))
    }

    private fun getCurrentPublishDateAsCalendar(postModel: PostModel): Calendar {
        val calendar = localeManagerWrapper.getCurrentCalendar()
        val dateCreated = postModel.dateCreated
        // Set the currently selected time if available
        if (!TextUtils.isEmpty(dateCreated)) {
            calendar.time = DateTimeUtils.dateFromIso8601(dateCreated)
            calendar.timeZone = localeManagerWrapper.getTimeZone()
        }
        return calendar
    }

    fun updatePost(updatedDate: Calendar, post: PostModel?) {
        post?.let {
            post.dateCreated = DateTimeUtils.iso8601FromDate(updatedDate.time)
            val initialPostStatus = PostStatus.fromPost(post)
            val isPublishDateInTheFuture = PostUtils.isPublishDateInTheFuture(post)
            var finalPostStatus = initialPostStatus
            if (initialPostStatus == DRAFT && isPublishDateInTheFuture) {
                // The previous logic was setting the status twice, once from draft to published and when the user
                // picked the time, it set it from published to scheduled. This is now done in one step.
                finalPostStatus = SCHEDULED
            } else if (initialPostStatus == PUBLISHED && post.isLocalDraft()) {
                // if user was changing dates for a local draft (not saved yet), only way to have it set to PUBLISH
                // is by running into the if case above. So, if they're updating the date again by calling
                // `updatePublishDate()`, get it back to DRAFT.
                finalPostStatus = DRAFT
            } else if (initialPostStatus == SCHEDULED && !isPublishDateInTheFuture) {
                // if this is a SCHEDULED post and the user is trying to Back-date it now, let's update it to DRAFT.
                // The other option was to make it published immediately but, let the user actively do that rather than
                // having the app be smart about it - we don't want to accidentally publish a post.
                finalPostStatus = DRAFT
                // show toast only once, when time is shown
                _onToast.postValue(Event(resourceProvider.getString(R.string.editor_post_converted_back_to_draft)))
            }
            post.status = finalPostStatus.toString()
            _onPostStatusChanged.value = finalPostStatus
            updateUiModel(post = post)
        }
    }

    fun updateUiModel(updatedNotificationTime: NotificationTime? = onNotificationTime.value, post: PostModel?) {
        if (post != null) {
            val publishDateLabel = postSettingsUtils.getPublishDateLabel(post)
            val futureTime = localeManagerWrapper.getCurrentCalendar().timeInMillis + 6000
            val now = localeManagerWrapper.getCurrentCalendar().timeInMillis - 10000
            val dateCreated = (DateTimeUtils.dateFromIso8601(post.dateCreated)
                    ?: localeManagerWrapper.getCurrentCalendar().time).time
            val enableNotification = dateCreated > futureTime
            val showNotification = dateCreated > now
            val notificationTime = if (updatedNotificationTime != null && enableNotification && showNotification) {
                updatedNotificationTime
            } else {
                OFF
            }
            _onUiModel.value = PublishUiModel(
                    publishDateLabel,
                    notificationTime = notificationTime,
                    notificationEnabled = enableNotification,
                    notificationVisible = showNotification
            )
        } else {
            _onUiModel.value = PublishUiModel(resourceProvider.getString(R.string.immediately))
        }
    }

    fun createNotification(notificationTime: NotificationTime) {
        _onNotificationTime.value = notificationTime
    }

    data class PublishUiModel(
        val publishDateLabel: String,
        val notificationTime: NotificationTime = OFF,
        val notificationEnabled: Boolean = false,
        val notificationVisible: Boolean = true
    )
}
