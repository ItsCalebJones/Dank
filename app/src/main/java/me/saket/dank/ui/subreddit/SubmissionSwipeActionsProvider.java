package me.saket.dank.ui.subreddit;

import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;

import com.jakewharton.rxrelay2.PublishRelay;

import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import me.saket.dank.R;
import me.saket.dank.data.OnLoginRequireListener;
import me.saket.dank.data.PostedOrInFlightContribution;
import me.saket.dank.data.VotingManager;
import me.saket.dank.ui.submission.SubmissionRepository;
import me.saket.dank.ui.user.UserSessionRepository;
import me.saket.dank.utils.Animations;
import me.saket.dank.widgets.swipe.SwipeAction;
import me.saket.dank.widgets.swipe.SwipeActionIconView;
import me.saket.dank.widgets.swipe.SwipeActions;
import me.saket.dank.widgets.swipe.SwipeActionsHolder;
import me.saket.dank.widgets.swipe.SwipeTriggerRippleDrawable.RippleType;
import me.saket.dank.widgets.swipe.SwipeableLayout;
import timber.log.Timber;

/**
 * Controls gesture actions on submissions.
 */
public class SubmissionSwipeActionsProvider implements SwipeableLayout.SwipeActionIconProvider {

  private static final String ACTION_NAME_SAVE = "Save";
  private static final String ACTION_NAME_UNSAVE = "UnSave";
  private static final String ACTION_NAME_OPTIONS = "Options";
  private static final String ACTION_NAME_UPVOTE = "Upvote";
  private static final String ACTION_NAME_DOWNVOTE = "Downvote";

  private final SwipeActions swipeActionsWithUnsave;
  private final SwipeActions swipeActionsWithSave;
  private final SubmissionRepository submissionRepository;
  private VotingManager votingManager;
  private final UserSessionRepository userSessionRepository;
  private final OnLoginRequireListener onLoginRequireListener;
  private final PublishRelay<SubmissionOptionSwipeEvent> optionSwipeActions = PublishRelay.create();

  @Inject
  public SubmissionSwipeActionsProvider(
      SubmissionRepository submissionRepository,
      VotingManager votingManager,
      UserSessionRepository userSessionRepository,
      OnLoginRequireListener onLoginRequireListener)
  {
    this.submissionRepository = submissionRepository;
    this.votingManager = votingManager;
    this.userSessionRepository = userSessionRepository;
    this.onLoginRequireListener = onLoginRequireListener;

    SwipeAction saveSwipeAction = SwipeAction.create(ACTION_NAME_SAVE, R.color.list_item_swipe_save, 1f);
    SwipeAction unSaveSwipeAction = SwipeAction.create(ACTION_NAME_UNSAVE, R.color.list_item_swipe_save, 1f);
    SwipeAction moreOptionsSwipeAction = SwipeAction.create(ACTION_NAME_OPTIONS, R.color.list_item_swipe_more_options, 1f);
    SwipeAction downvoteSwipeAction = SwipeAction.create(ACTION_NAME_DOWNVOTE, R.color.list_item_swipe_downvote, 1f);
    SwipeAction upvoteSwipeAction = SwipeAction.create(ACTION_NAME_UPVOTE, R.color.list_item_swipe_upvote, 1f);

    swipeActionsWithUnsave = SwipeActions.builder()
        .startActions(SwipeActionsHolder.builder()
            .add(unSaveSwipeAction)
            .add(moreOptionsSwipeAction)
            .build())
        .endActions(SwipeActionsHolder.builder()
            .add(downvoteSwipeAction)
            .add(upvoteSwipeAction)
            .build())
        .build();

    swipeActionsWithSave = SwipeActions.builder()
        .startActions(SwipeActionsHolder.builder()
            .add(saveSwipeAction)
            .add(moreOptionsSwipeAction)
            .build())
        .endActions(SwipeActionsHolder.builder()
            .add(downvoteSwipeAction)
            .add(upvoteSwipeAction)
            .build())
        .build();
  }

  @CheckResult
  public Observable<SubmissionOptionSwipeEvent> optionSwipeActions() {
    return optionSwipeActions;
  }

  public SwipeActions actionsFor(Submission submission) {
    boolean isSubmissionSaved = submissionRepository.isSaved(submission);
    return isSubmissionSaved ? swipeActionsWithUnsave : swipeActionsWithSave;
  }

  @Override
  public void showSwipeActionIcon(SwipeActionIconView imageView, @Nullable SwipeAction oldAction, SwipeAction newAction) {
    switch (newAction.name()) {
      case ACTION_NAME_OPTIONS:
        resetIconRotation(imageView);
        imageView.setImageResource(R.drawable.ic_more_horiz_24dp);
        break;

      case ACTION_NAME_SAVE:
        resetIconRotation(imageView);
        imageView.setImageResource(R.drawable.ic_star_24dp);
        break;

      case ACTION_NAME_UNSAVE:
        resetIconRotation(imageView);
        imageView.setImageResource(R.drawable.ic_star_border_24dp);
        break;

      case ACTION_NAME_UPVOTE:
        if (oldAction != null && ACTION_NAME_DOWNVOTE.equals(oldAction.name())) {
          imageView.setRotation(180);   // We want to play a circular animation if the user keeps switching between upvote and downvote.
          imageView.animate().rotationBy(180).setInterpolator(Animations.INTERPOLATOR).setDuration(200).start();
        } else {
          resetIconRotation(imageView);
          imageView.setImageResource(R.drawable.ic_arrow_upward_24dp);
        }
        break;

      case ACTION_NAME_DOWNVOTE:
        if (oldAction != null && ACTION_NAME_UPVOTE.equals(oldAction.name())) {
          imageView.setRotation(0);
          imageView.animate().rotationBy(180).setInterpolator(Animations.INTERPOLATOR).setDuration(200).start();
        } else {
          resetIconRotation(imageView);
          imageView.setImageResource(R.drawable.ic_arrow_downward_24dp);
        }
        break;

      default:
        throw new UnsupportedOperationException("Unknown swipe action: " + newAction);
    }
  }

  public void performSwipeAction(SwipeAction swipeAction, Submission submission, SwipeableLayout swipeableLayout) {
    if (!ACTION_NAME_OPTIONS.equals(swipeAction.name()) && !userSessionRepository.isUserLoggedIn()) {
      onLoginRequireListener.onLoginRequired();
      return;
    }

    boolean isUndoAction;

    switch (swipeAction.name()) {
      case ACTION_NAME_OPTIONS:
        optionSwipeActions.accept(SubmissionOptionSwipeEvent.create(submission, swipeableLayout));
        isUndoAction = false;
        break;

      case ACTION_NAME_SAVE:
        submissionRepository.markAsSaved(submission);
        isUndoAction = false;
        Timber.i("TODO: %s", swipeAction.name());
        break;

      case ACTION_NAME_UNSAVE:
        submissionRepository.markAsUnsaved(submission);
        isUndoAction = true;
        Timber.i("TODO: %s", swipeAction.name());
        break;

      case ACTION_NAME_UPVOTE: {
        PostedOrInFlightContribution submissionInfo = PostedOrInFlightContribution.from(submission);
        VoteDirection currentVoteDirection = votingManager.getPendingOrDefaultVote(submissionInfo, submissionInfo.voteDirection());
        VoteDirection newVoteDirection = currentVoteDirection == VoteDirection.UPVOTE ? VoteDirection.NO_VOTE : VoteDirection.UPVOTE;
        votingManager.voteWithAutoRetry(submissionInfo, newVoteDirection)
            .subscribeOn(Schedulers.io())
            .subscribe();

        isUndoAction = newVoteDirection == VoteDirection.NO_VOTE;
        break;
      }

      case ACTION_NAME_DOWNVOTE: {
        PostedOrInFlightContribution submissionInfo = PostedOrInFlightContribution.from(submission);
        VoteDirection currentVoteDirection = votingManager.getPendingOrDefaultVote(submissionInfo, submissionInfo.voteDirection());
        VoteDirection newVoteDirection = currentVoteDirection == VoteDirection.DOWNVOTE ? VoteDirection.NO_VOTE : VoteDirection.DOWNVOTE;
        votingManager.voteWithAutoRetry(submissionInfo, newVoteDirection)
            .subscribeOn(Schedulers.io())
            .subscribe();

        isUndoAction = newVoteDirection == VoteDirection.NO_VOTE;
        break;
      }

      default:
        throw new UnsupportedOperationException("Unknown swipe action: " + swipeAction);
    }

    swipeableLayout.playRippleAnimation(swipeAction, isUndoAction ? RippleType.UNDO : RippleType.REGISTER);
  }

  private void resetIconRotation(SwipeActionIconView imageView) {
    imageView.animate().cancel();
    imageView.setRotation(0);
  }
}