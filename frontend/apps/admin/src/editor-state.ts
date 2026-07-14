export interface EditorState {
  coverFile: globalThis.File | null;
  coverInput: Pick<globalThis.HTMLInputElement, 'value'> | null;
}

export function createEditorState(): EditorState {
  return {
    coverFile: null,
    coverInput: null
  };
}

export function resetEditorState(state: EditorState): void {
  state.coverFile = null;
  if (state.coverInput) {
    state.coverInput.value = '';
  }
}
