import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  checkRootAccess(): Promise<boolean>;
  startDeepScan(): Promise<void>;
  stopScan(): Promise<void>;
  restoreFile(carvedFilePath: string): Promise<boolean>;
  // Required for NativeEventEmitter
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeRecovery');