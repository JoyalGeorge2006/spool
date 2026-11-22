import { create } from 'zustand';
import { persist } from 'zustand/middleware';
/** Only still artist artwork is retained. Old motion preferences are ignored. */
export const useAppleSettings = create<{ artistArt: boolean; setArtistArt: (on: boolean) => void }>()(
  persist((set) => ({ artistArt: true, setArtistArt: (artistArt) => set({ artistArt }) }),
    { name: 'bitchord:apple', version: 1, merge: (saved, current) => ({ ...current, artistArt: (saved as { artistArt?: boolean })?.artistArt ?? true }) }),
);
