package teammates.client.scripts;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jdo.Query;

import teammates.client.remoteapi.RemoteApiClient;
import teammates.common.datatransfer.FeedbackQuestionAttributes;
import teammates.common.datatransfer.FeedbackSessionAttributes;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Assumption;
import teammates.logic.api.Logic;
import teammates.storage.api.FeedbackQuestionsDb;
import teammates.storage.api.QuestionsDb;
import teammates.storage.datastore.Datastore;
import teammates.storage.entity.FeedbackQuestion;

/**
 * Script to create a Question copy of old FeedbackQuestions.
 * 
 */
public class DataMigrationForFeedbackQuestionsToQuestions extends RemoteApiClient {
    
    /**
     * 
     * BY_TIME: migration will affect questions created in the past {@code numDays} days
     * BY_COURSE: migration will affects questions in the specified {@code courseId}
     * ALL: all questions will be migrated
     */
    private enum ScriptTarget {
        BY_TIME, BY_COURSE, ALL;
    }
    
    private static final boolean isPreview = true;
    
    private static final ScriptTarget target = ScriptTarget.ALL;
    
    // When using ScriptTarget.BY_TIME, numDays can be changed to target
    // questions created in the past number of days
    private static final int numDays = 100;
    
    // When using ScriptTarget.BY_COURSE, specify the course to target with courseId
    private static final String courseId = "";
    
    public static void main(String[] args) throws IOException {
        new DataMigrationForFeedbackQuestionsToQuestions().doOperationRemotely();
    }

    @Override
    protected void doOperation() {
        
        Datastore.initialize();
        List<FeedbackQuestion> feedbackQuestionEntities;
        if (target == ScriptTarget.BY_TIME) {
            Calendar startCal = Calendar.getInstance();
            startCal.add(Calendar.DAY_OF_YEAR, -1 * numDays);
            
            feedbackQuestionEntities = getOldQuestionsSince(startCal.getTime());
            
        } else if (target == ScriptTarget.BY_COURSE) {
            feedbackQuestionEntities = getFeedbackQuestionEntitiesForCourse(courseId);
            
        } else if (target == ScriptTarget.ALL) {
            feedbackQuestionEntities = getAllOldQuestions();
            
        } else {
            feedbackQuestionEntities = null;
            Assumption.fail("no target selected");
        }
        
        List<FeedbackQuestionAttributes> feedbackQuestions =
                FeedbackQuestionsDb.getListOfQuestionAttributes(feedbackQuestionEntities);
        System.out.println("Size of feedbackQuestions = " + feedbackQuestions.size());
        int i = 0;
        for (FeedbackQuestionAttributes old : feedbackQuestions) {
            i += 1;
            FeedbackSessionAttributes session = new Logic().getFeedbackSession(
                    old.getFeedbackSessionName(), old.getCourseId());
            if (session == null) {
                System.out.println(i + ". Question: " + old.getIdentificationString());
                System.out.println(String.format("Error finding session %s",
                                                 old.getFeedbackSessionName() + ":"
                                                 + old.getCourseId()));
                System.out.println("possibly due to orphaned responses");
                
                continue;
            }
            
            if (isPreview) {
                FeedbackQuestionAttributes existingQn =
                        new QuestionsDb().getFeedbackQuestion(old.feedbackSessionName, old.courseId, old.getId());
                if (existingQn == null) {
                    System.out.println(i + ". Will create question: " + old.getIdentificationString());
                } else {
                    System.out.println(i + ". New question type entity already exists for question:"
                                       + existingQn.getIdentificationString());
                }
            } else {
                try {
                    new QuestionsDb().createFeedbackQuestion(session, old);
                    System.out.println(i + ". Created question: " + old.getIdentificationString());
                } catch (EntityDoesNotExistException | InvalidParametersException e) {
                    e.printStackTrace();
                    throw new RuntimeException(
                            String.format(i + ". Unable to update existing session %s with question %s",
                                    session.getIdentificationString(),
                                    old.getIdentificationString()),
                                    e);
                } catch (EntityAlreadyExistsException e) {
                    // ignore if a copy of the old question already exists
                    System.out.println(i + ". New question type entity already exists for question:"
                                       + old.getIdentificationString());
                }
            }
            
        }
    }

    private List<FeedbackQuestion> getFeedbackQuestionEntitiesForCourse(String courseId) {
    
        Query q = Datastore.getPersistenceManager().newQuery(FeedbackQuestion.class);
        q.declareParameters("String courseIdParam");
        q.setFilter("courseId == courseIdParam");
        
        @SuppressWarnings("unchecked")
        List<FeedbackQuestion> feedbackQuestionList = (List<FeedbackQuestion>) q.execute(courseId);
        
        return feedbackQuestionList;
    }

    private List<FeedbackQuestion> getAllOldQuestions() {
        String query = "SELECT FROM " + FeedbackQuestion.class.getName();
        @SuppressWarnings("unchecked")
        List<FeedbackQuestion> feedbackQuestions =
                (List<FeedbackQuestion>) Datastore.getPersistenceManager().newQuery(query).execute();
        return feedbackQuestions;
    }
    
    private List<FeedbackQuestion> getOldQuestionsSince(Date date) {
        String query = "SELECT FROM " + FeedbackQuestion.class.getName()
                        + " WHERE updatedAt >= startDate"
                        + " PARAMETERS java.util.Date startDate";
        @SuppressWarnings("unchecked")
        List<FeedbackQuestion> feedbackQuestions =
                (List<FeedbackQuestion>) Datastore.getPersistenceManager().newQuery(query).execute(date);
        return feedbackQuestions;
    }
}
