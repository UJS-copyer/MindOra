import { describe, expect, it } from 'vitest';
import { anchorIdFromHeadingPath, anchorIdFromText } from './anchor';

describe('anchorIdFromText', () => {
  it('keeps CJK characters and lowercases latin text', () => {
    expect(anchorIdFromText('检索链路')).toBe('检索链路');
    expect(anchorIdFromText('RRF Fusion')).toBe('rrf-fusion');
  });

  it('collapses punctuation and spaces into single dashes', () => {
    expect(anchorIdFromText('2.1 滑动窗口 —— 切片算法')).toBe('2-1-滑动窗口-切片算法');
  });

  it('drops inline markdown markup', () => {
    expect(anchorIdFromText('**重点** `code`')).toBe('重点-code');
  });

  it('falls back to a stable id for empty or symbol-only titles', () => {
    expect(anchorIdFromText('   ')).toBe('heading');
    expect(anchorIdFromText('!!!')).toBe('heading');
  });

  it('trims leading and trailing dashes', () => {
    expect(anchorIdFromText('  混合检索  ')).toBe('混合检索');
  });
});

describe('anchorIdFromHeadingPath', () => {
  it('uses the last segment of a heading path', () => {
    expect(anchorIdFromHeadingPath('检索链路 > 融合与重排')).toBe('融合与重排');
  });

  it('tolerates extra whitespace and empty segments', () => {
    expect(anchorIdFromHeadingPath('A >  > B ')).toBe('b');
  });

  it('returns null when there is nothing to anchor to', () => {
    expect(anchorIdFromHeadingPath(null)).toBeNull();
    expect(anchorIdFromHeadingPath('')).toBeNull();
    expect(anchorIdFromHeadingPath('  >  ')).toBeNull();
  });
});
