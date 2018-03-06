package com.capitalone.dashboard.collector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.capitalone.dashboard.ScoreSettingsService;
import com.capitalone.dashboard.model.score.ScoreMetric;
import com.capitalone.dashboard.model.score.ScoreValueType;
import com.capitalone.dashboard.model.score.settings.ScoreCriteriaSettings;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;

import com.capitalone.dashboard.ApplicationScoreService;
import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.repository.*;

/**
 * Collects {@link ScoreMetric} data from
 * {@link ScoreApplication}s.
 */
@org.springframework.stereotype.Component
public class ScoreCollectorTask extends CollectorTask<ScoreCollector> {
  @SuppressWarnings({"unused", "PMD.UnusedPrivateField"})
  private static final Logger LOGGER = LoggerFactory.getLogger(ScoreCollectorTask.class);

  private final ScoreCollectorRepository scoreCollectorRepository;
  private final ScoreApplicationRepository scoreApplicationRepository;
  private final ScoreSettings scoreSettings;
  private final ScoreSettingsService scoreSettingsService;
  private final ScoreRepository scoreRepository;
  private final ScoreCriteriaSettingsRepository scoreCriteriaSettingsRepository;

  private final ComponentRepository dbComponentRepository;
  private final ApplicationScoreService applicationScoreService;

  @Autowired
  @SuppressWarnings("PMD.ExcessiveParameterList")
  public ScoreCollectorTask(TaskScheduler taskScheduler,
    ScoreCollectorRepository scoreCollectorRepository,
    ScoreApplicationRepository scoreApplicationRepository,
    ScoreRepository scoreRepository,
    ScoreSettings scoreSettings,
    ScoreSettingsService scoreSettingsService,
    ScoreCriteriaSettingsRepository scoreCriteriaSettingsRepository,
    ComponentRepository dbComponentRepository,
    ApplicationScoreService applicationScoreService) {
    super(taskScheduler, "Score");
    this.scoreCollectorRepository = scoreCollectorRepository;
    this.scoreApplicationRepository = scoreApplicationRepository;
    this.scoreSettings = scoreSettings;
    this.scoreSettingsService = scoreSettingsService;
    this.scoreRepository = scoreRepository;
    this.scoreCriteriaSettingsRepository = scoreCriteriaSettingsRepository;
    this.dbComponentRepository = dbComponentRepository;
    this.applicationScoreService = applicationScoreService;

  }

  @Override
  public ScoreCollector getCollector() {
    return ScoreCollector.prototype();
  }

  @Override
  public BaseCollectorRepository<ScoreCollector> getCollectorRepository() {
    return scoreCollectorRepository;
  }

  @Override
  public String getCron() {
    return scoreSettings.getCron();
  }

  @Override
  @SuppressWarnings("PMD.ExcessiveMethodLength")
  public void collect(ScoreCollector collector) {

    logBanner("Score");

    long start = System.currentTimeMillis();

    //Save Dashboard Score Criteria Settings when collector runs
    ScoreCriteriaSettings scoreCriteriaSettings = saveDashboardScoreSettings();

    //Find enabled applications from collection repository
    //For each enabled application, get the dashboard id
    //For the dashboard id get the dashboard data
    //For each dashboard go through the widgets one by one


    //Get all dashboards with score widget
    List<ScoreApplication> scoreApplications = getScoreApplications(collector);
    log("No of dashboards with score widget=" + scoreApplications.size());
    for (ScoreApplication scoreApplication : scoreApplications) {
      collectScoreForApplication(scoreApplication, scoreCriteriaSettings);
    }
    clean(collector);
    log("Finished", start);
  }


  private void collectScoreForApplication(ScoreApplication scoreApplication, ScoreCriteriaSettings scoreCriteriaSettings) {

    ScoreMetric scoreMetric = this.applicationScoreService.getScoreForApplication(scoreApplication, scoreCriteriaSettings);

    if (null == scoreMetric) {
      return;
    }

    ScoreMetric existingScore = this.scoreRepository
      .findByCollectorItemId(scoreApplication.getId());
    if (null != existingScore) {
      scoreMetric.setId(existingScore.getId());
    }
    this.scoreRepository.save(scoreMetric);
  }


  private List<ScoreApplication> getScoreApplications(ScoreCollector collector) {
    log("Score collector id = " + collector.getId());
    return scoreApplicationRepository.findEnabledScores(
      collector.getId());
  }

  /**
   * Clean up unused score collector items
   *
   * @param collector the {@link ScoreCollector}
   */
  @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
  private void clean(ScoreCollector collector) {
    Set<ObjectId> uniqueIDs = new HashSet<>();

    for (Component comp : dbComponentRepository
      .findAll()) {
      if (comp.getCollectorItems() == null || comp.getCollectorItems().isEmpty())
        continue;
      List<CollectorItem> itemList = comp.getCollectorItems().get(
        CollectorType.Score);
      if (itemList == null)
        continue;
      for (CollectorItem ci : itemList) {
        if (ci == null)
          continue;
        uniqueIDs.add(ci.getId());
      }
    }
    List<ScoreApplication> appList = new ArrayList<>();
    Set<ObjectId> udId = new HashSet<>();
    udId.add(collector.getId());
    for (ScoreApplication app : scoreApplicationRepository.findByCollectorIdIn(udId)) {
      if (app != null) {
        app.setEnabled(uniqueIDs.contains(app.getId()));
        appList.add(app);
      }
    }
    scoreApplicationRepository.save(appList);
  }


  private ScoreCriteriaSettings saveDashboardScoreSettings() {
    ScoreCriteriaSettings scoreCriteriaSettings = this.scoreSettingsService.getDashboardScoreCriteriaSettings();
    scoreCriteriaSettings.setTimestamp(System.currentTimeMillis());

    ScoreCriteriaSettings scoreCriteriaSettingsExisting = this.scoreCriteriaSettingsRepository.findByType(ScoreValueType.DASHBOARD);

    if (null != scoreCriteriaSettingsExisting) {
      scoreCriteriaSettings.setId(scoreCriteriaSettingsExisting.getId());
    }

    return this.scoreCriteriaSettingsRepository.save(scoreCriteriaSettings);
  }

}
