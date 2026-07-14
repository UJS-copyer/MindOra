import { describe, expect, it } from 'vitest';
import { createEditorState, resetEditorState } from './editor-state';

describe('resetEditorState', () => {
  it('clears a pending cover file when switching articles', () => {
    const state = createEditorState();
    const coverInput = { value: 'C:\\fakepath\\cover.png' };
    state.coverFile = new globalThis.File(['data'], 'cover.png', { type: 'image/png' });
    state.coverInput = coverInput;

    resetEditorState(state);

    expect(state.coverFile).toBeNull();
    expect(coverInput.value).toBe('');
  });
});
