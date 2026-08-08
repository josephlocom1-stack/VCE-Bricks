import { StatusBar } from 'expo-status-bar';
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { DailyChallengeScreen } from './src/features/daily/DailyChallengeScreen';
import { BossRoundScreen } from './src/features/game/BossRoundScreen';
import { QuickSprintScreen } from './src/features/game/QuickSprintScreen';
import { HomeScreen } from './src/features/home/HomeScreen';
import { MistakeWorkoutScreen } from './src/features/mistakes/MistakeWorkoutScreen';
import { OnboardingScreen } from './src/features/onboarding/OnboardingScreen';
import { PracticeScreen } from './src/features/practice/PracticeScreen';
import { ProgressScreen } from './src/features/progress/ProgressScreen';
import { ResultsScreen } from './src/features/results/ResultsScreen';
import { SettingsScreen } from './src/features/settings/SettingsScreen';
import { createDefaultStoredData } from './src/lib/progress/defaults';
import { markMistakeImproved, recordRoundResult, resetUserProgress, saveSettings, getStoredAppData } from './src/lib/storage/storage';
import { colors, radius, spacing } from './src/theme/tokens';
import { AppSettings, Difficulty, GameMode, GameRoundResult, StoredAppData, SubjectId } from './src/types';

type ScreenKey = 'home' | 'practice' | 'quickSprint' | 'boss' | 'progress' | 'settings' | 'mistakes' | 'daily' | 'results';
const tabs: { key: ScreenKey; label: string }[] = [
  { key: 'home', label: 'Home' }, { key: 'practice', label: 'Practice' }, { key: 'quickSprint', label: 'Sprint' }, { key: 'progress', label: 'Progress' }, { key: 'settings', label: 'Settings' },
];

export default function App() {
  const [data, setData] = useState<StoredAppData>(createDefaultStoredData());
  const [screen, setScreen] = useState<ScreenKey>('home');
  const [selectedSubject, setSelectedSubject] = useState<SubjectId>('addition');
  const [selectedDifficulty, setSelectedDifficulty] = useState<Difficulty>('beginner');
  const [currentMode, setCurrentMode] = useState<GameMode>('quickSprint');
  const [lastResult, setLastResult] = useState<GameRoundResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const now = Date.now();
  const dueMistakes = data.mistakes.filter((mistake) => mistake.status !== 'improved' && mistake.nextDueAt <= now);

  useEffect(() => {
    let mounted = true;
    getStoredAppData().then((stored) => { if (mounted) { setData(stored); setSelectedDifficulty(stored.settings.preferredDifficulty); } }).finally(() => { if (mounted) setIsLoading(false); });
    return () => { mounted = false; };
  }, []);

  function openRound(mode: GameMode, subject = selectedSubject) { setCurrentMode(mode); setSelectedSubject(subject); setScreen('quickSprint'); }
  function openTab(tab: ScreenKey) { if (tab === 'quickSprint') { openRound('quickSprint', 'mixed'); return; } setScreen(tab); }
  async function onRoundComplete(result: GameRoundResult) {
    let nextData = await recordRoundResult(result);
    if (result.mode === 'mistakeWorkout') {
      for (const attempt of result.attempts.filter((item) => item.isCorrect)) {
        const matched = nextData.mistakes.find((mistake) => mistake.question.prompt === attempt.question.prompt && mistake.question.correctAnswer === attempt.question.correctAnswer);
        if (matched) nextData = await markMistakeImproved(matched.id);
      }
    }
    setData(nextData); setLastResult(result); setScreen('results');
  }
  function startSubjectPractice(subject: SubjectId) { openRound('practice', subject); }
  async function onSaveSettings(settings: AppSettings) { const nextData = await saveSettings(settings); setData(nextData); setSelectedDifficulty(settings.preferredDifficulty); }
  async function onResetProgress() { const nextData = await resetUserProgress(); setData(nextData); setLastResult(null); setScreen('home'); }
  async function completeOnboarding() { const nextData = await saveSettings({ ...data.settings, onboardingComplete: true }); setData(nextData); }

  function renderScreen() {
    if (isLoading) return <View style={styles.loading}><ActivityIndicator color={colors.blue600} /><Text style={styles.loadingText}>Loading Numbit...</Text></View>;
    if (!data.settings.onboardingComplete) return <OnboardingScreen onComplete={completeOnboarding} />;
    if (screen === 'practice') return <PracticeScreen data={data} difficulty={selectedDifficulty} onDifficultyChange={setSelectedDifficulty} onSelectSubject={startSubjectPractice} />;
    if (screen === 'quickSprint') return <QuickSprintScreen mode={currentMode} subject={currentMode === 'dailyChallenge' ? data.dailyMission.subject : selectedSubject} difficulty={currentMode === 'dailyChallenge' ? data.dailyMission.difficulty : selectedDifficulty} mistakes={dueMistakes} progress={data.progress} hapticsEnabled={data.settings.hapticsEnabled} onComplete={onRoundComplete} onExit={() => setScreen('home')} />;
    if (screen === 'boss') return <BossRoundScreen hapticsEnabled={data.settings.hapticsEnabled} onComplete={onRoundComplete} onExit={() => setScreen('home')} />;
    if (screen === 'progress') return <ProgressScreen data={data} onStartPractice={() => setScreen('practice')} />;
    if (screen === 'settings') return <SettingsScreen settings={data.settings} onSaveSettings={onSaveSettings} onResetProgress={onResetProgress} />;
    if (screen === 'mistakes') return <MistakeWorkoutScreen data={data} onStartWorkout={() => openRound('mistakeWorkout', dueMistakes[0]?.subject ?? 'addition')} onStartPractice={() => setScreen('practice')} />;
    if (screen === 'daily') return <DailyChallengeScreen data={data} onStartChallenge={() => openRound('dailyChallenge', data.dailyMission.subject)} />;
    if (screen === 'results' && lastResult) return <ResultsScreen result={lastResult} onPlayAgain={() => lastResult.mode === 'bossRound' ? setScreen('boss') : openRound(lastResult.mode, lastResult.subject)} onReviewMistakes={() => setScreen('mistakes')} onHome={() => setScreen('home')} />;
    return <HomeScreen data={data} onStartQuick={() => openRound('quickSprint', 'mixed')} onStartFixed25={() => openRound('fixed25', 'mixed')} onStartSurvival={() => openRound('survival', 'mixed')} onStartAdaptive={() => openRound('adaptive', 'mixed')} onStartBoss={() => setScreen('boss')} onOpenPractice={() => setScreen('practice')} onOpenDaily={() => setScreen('daily')} onOpenMistakes={() => setScreen('mistakes')} onSelectSubject={startSubjectPractice} />;
  }

  const showTabs = data.settings.onboardingComplete && !['quickSprint', 'boss', 'results'].includes(screen);
  return <SafeAreaView style={styles.safeArea}><StatusBar style="light" /><View style={styles.appShell}>{renderScreen()}{showTabs ? <View style={styles.tabBar}>{tabs.map((tab) => { const active = screen === tab.key || (screen === 'daily' && tab.key === 'home') || (screen === 'mistakes' && tab.key === 'home'); return <Pressable key={tab.key} accessibilityRole="tab" accessibilityLabel={`Open ${tab.label}`} accessibilityState={{ selected: active }} hitSlop={6} onPress={() => openTab(tab.key)} style={styles.tab}><View style={[styles.tabIndicator, active && styles.activeTabIndicator]} /><Text style={[styles.tabLabel, active && styles.activeTabText]}>{tab.label}</Text></Pressable>; })}</View> : null}</View></SafeAreaView>;
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.surface50 }, appShell: { flex: 1, backgroundColor: colors.surface50 }, loading: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md }, loadingText: { color: colors.ink700, fontWeight: '700' },
  tabBar: { position: 'absolute', left: spacing.md, right: spacing.md, bottom: spacing.md, minHeight: 70, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.line100, backgroundColor: colors.surface0, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingHorizontal: spacing.sm, paddingVertical: spacing.sm },
  tab: { flex: 1, minHeight: 52, alignItems: 'center', justifyContent: 'center', gap: 2 }, tabIndicator: { width: 18, height: 4, borderRadius: 999, backgroundColor: colors.line100 }, activeTabIndicator: { width: 28, backgroundColor: colors.blue600 }, tabLabel: { color: colors.ink500, fontSize: 11, fontWeight: '800' }, activeTabText: { color: colors.blue600 },
});
