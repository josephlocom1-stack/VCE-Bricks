import { DailyMission, GameRoundResult, MistakeItem, RoundHistoryItem, StoredAppData, SubjectId, TopicStats, UserProgress } from '../../types';
import { getWeekdayIndex, getLocalDate, daysBetween } from '../date/localDate';
import { calculatePerformanceMetrics, getLevelFromXp } from '../game/scoring';
import { createDefaultDailyMission, createDefaultTopicStats } from './defaults';

const subjectIds: SubjectId[] = ['addition','subtraction','multiplication','division','timesTables','mixed','decimals','fractions','percentages','negativeNumbers','missingNumber','squaresRoots'];
const maxRoundHistory = 1000;

function calculateTopicLevel(stats: TopicStats): number {
  const accuracy = stats.attempts ? stats.correct / stats.attempts : 0;
  const accuracyBonus = accuracy >= 0.9 ? 2 : accuracy >= 0.8 ? 1 : 0;
  return Math.max(1, Math.min(10, Math.floor(stats.xp / 250) + 1 + accuracyBonus));
}
function resolveAttemptSubject(round: GameRoundResult, tags: string[], questionSubject: SubjectId): SubjectId {
  if (questionSubject !== 'mixed') return questionSubject;
  const tagged = tags.map((tag) => tag.startsWith('subject:') ? tag.slice('subject:'.length) : '').find((value) => value && value !== 'mixed' && subjectIds.includes(value as SubjectId));
  return (tagged as SubjectId | undefined) ?? round.subject;
}
function updateStreak(progress: UserProgress, timestamp: number): UserProgress {
  const today = getLocalDate(timestamp);
  if (progress.lastPracticeDate === today) return progress;
  const gap = progress.lastPracticeDate ? daysBetween(progress.lastPracticeDate, today) : 0;
  const currentStreak = gap === 1 ? progress.currentStreak + 1 : 1;
  return { ...progress, currentStreak, bestStreak: Math.max(progress.bestStreak, currentStreak), lastPracticeDate: today };
}
export function recordRoundResult(data: StoredAppData, round: GameRoundResult): StoredAppData {
  const progressWithStreak = updateStreak(data.progress, round.endedAt);
  const weekday = getWeekdayIndex(round.endedAt);
  const weeklyActivity = [...progressWithStreak.weeklyActivity];
  weeklyActivity[weekday] = (weeklyActivity[weekday] ?? 0) + round.xpEarned;
  const topicStats = { ...progressWithStreak.topicStats };
  for (const attempt of round.attempts) {
    const subject = resolveAttemptSubject(round, attempt.question.tags, attempt.question.subject);
    const existing = topicStats[subject] ?? createDefaultTopicStats(subject);
    const recentResults = [...(existing.recentResults ?? []), attempt.isCorrect].slice(-24);
    const recentAnswerTimesMs = [...(existing.recentAnswerTimesMs ?? []), Math.max(0, attempt.timeToAnswerMs)].slice(-24);
    const nextStats: TopicStats = { ...existing, attempts: existing.attempts + 1, correct: existing.correct + (attempt.isCorrect ? 1 : 0), bestScore: Math.max(existing.bestScore, attempt.scoreEarned), xp: existing.xp + attempt.xpEarned, lastPracticedAt: round.endedAt, recentResults, recentAnswerTimesMs };
    topicStats[subject] = { ...nextStats, level: calculateTopicLevel(nextStats) };
  }
  const newMistakes: MistakeItem[] = round.attempts.filter((attempt) => !attempt.isCorrect).map((attempt) => ({ id: `mistake-${attempt.id}`, question: attempt.question, lastAnswer: attempt.userAnswer, subject: resolveAttemptSubject(round, attempt.question.tags, attempt.question.subject), status: 'due', timesSeen: 1, timesCorrectAfter: 0, createdAt: attempt.answeredAt, lastSeenAt: attempt.answeredAt, nextDueAt: attempt.answeredAt }));
  const performance = calculatePerformanceMetrics(round.attempts, round.difficulty);
  const roundAnswerTime = round.attempts.reduce((sum, attempt) => sum + Math.max(0, attempt.timeToAnswerMs), 0);
  const correctTimes = round.attempts.filter((attempt) => attempt.isCorrect).map((attempt) => attempt.timeToAnswerMs).filter((value) => value > 0);
  const roundFastest = correctTimes.length ? Math.min(...correctTimes) : 0;
  const previousFastest = progressWithStreak.fastestCorrectMs ?? 0;
  const progress: UserProgress = { ...progressWithStreak, totalXp: progressWithStreak.totalXp + round.xpEarned, level: getLevelFromXp(progressWithStreak.totalXp + round.xpEarned), totalAttempts: progressWithStreak.totalAttempts + round.attempts.length, totalCorrect: progressWithStreak.totalCorrect + round.correctCount, totalAnswerTimeMs: (progressWithStreak.totalAnswerTimeMs ?? 0) + roundAnswerTime, fastestCorrectMs: roundFastest > 0 && (previousFastest === 0 || roundFastest < previousFastest) ? roundFastest : previousFastest, bestMathRating: Math.max(progressWithStreak.bestMathRating ?? 0, performance.mathRating), bestScore: Math.max(progressWithStreak.bestScore, round.score), roundsCompleted: progressWithStreak.roundsCompleted + 1, quickSprintsCompleted: progressWithStreak.quickSprintsCompleted + (round.mode === 'quickSprint' ? 1 : 0), topicStats, weeklyActivity };
  const historyItem: RoundHistoryItem = { id: round.id, mode: round.mode, subject: round.subject, difficulty: round.difficulty, endedAt: round.endedAt, durationMs: Math.max(0, round.endedAt - round.startedAt), score: round.score, xpEarned: round.xpEarned, correctCount: round.correctCount, wrongCount: round.wrongCount, accuracy: performance.accuracy, medianAnswerTimeMs: performance.medianAnswerTimeMs, questionsPerMinute: performance.questionsPerMinute, consistency: performance.consistency, mathRating: performance.mathRating };
  return { ...data, progress, mistakes: dedupeMistakes([...newMistakes, ...data.mistakes]).slice(0, 80), roundHistory: [historyItem, ...(data.roundHistory ?? [])].slice(0, maxRoundHistory), dailyMission: updateDailyMissionAfterRound(data.dailyMission, round) };
}
function updateDailyMissionAfterRound(mission: DailyMission, round: GameRoundResult): DailyMission { if (mission.localDate !== getLocalDate(round.endedAt) || round.mode !== 'dailyChallenge') return mission; const accuracy = round.attempts.length ? Math.round((round.correctCount / round.attempts.length) * 100) : 0; return { ...mission, completed: true, passed: accuracy >= mission.targetAccuracy, progress: Math.min(100, accuracy) }; }
function dedupeMistakes(mistakes: MistakeItem[]): MistakeItem[] { const seen = new Set<string>(); return mistakes.filter((mistake) => { const key = `${mistake.question.prompt}:${mistake.question.correctAnswer}`; if (seen.has(key)) return false; seen.add(key); return true; }); }
export function markMistakeImprovedInData(data: StoredAppData, mistakeId: string): StoredAppData { const now = Date.now(); const target = data.mistakes.find((mistake) => mistake.id === mistakeId); if (!target) return data; const nextCorrectCount = target.timesCorrectAfter + 1; const becomesImproved = target.status !== 'improved' && nextCorrectCount >= 3; return { ...data, progress: { ...data.progress, mistakesImproved: data.progress.mistakesImproved + (becomesImproved ? 1 : 0) }, mistakes: data.mistakes.map((mistake) => mistake.id === mistakeId ? { ...mistake, status: nextCorrectCount >= 3 ? 'improved' : 'improving', timesSeen: mistake.timesSeen + 1, timesCorrectAfter: nextCorrectCount, lastSeenAt: now, nextDueAt: now + (nextCorrectCount >= 2 ? 3 : 1) * 86400000 } : mistake) }; }
function recentAccuracy(stats: TopicStats): number { const recent = stats.recentResults ?? []; if (recent.length >= 5) return recent.filter(Boolean).length / recent.length; return stats.attempts ? stats.correct / stats.attempts : 0; }
export function getStrongestTopic(progress: UserProgress): SubjectId | undefined { return Object.values(progress.topicStats).filter((stats) => stats.attempts >= 5).sort((a, b) => recentAccuracy(b) - recentAccuracy(a))[0]?.subject; }
export function getWeakestTopic(progress: UserProgress): SubjectId | undefined { return Object.values(progress.topicStats).filter((stats) => stats.attempts >= 5).sort((a, b) => recentAccuracy(a) - recentAccuracy(b))[0]?.subject; }
export function ensureDailyMission(data: StoredAppData): StoredAppData { const today = getLocalDate(); if (data.dailyMission?.localDate === today) return data; const subject = getWeakestTopic(data.progress) ?? 'addition'; return { ...data, dailyMission: createDefaultDailyMission(subject, today) }; }
