import React, { useState, useEffect } from 'react';
import { View, Button, Text, StyleSheet, FlatList, DeviceEventEmitter, Alert } from 'react-native';
import NativeRecovery from '../specs/NativeRecovery';

export default function RecoveryScreen() {
  const [isScanning, setIsScanning] = useState(false);
  const [rootStatus, setRootStatus] = useState<boolean | null>(null);
  const [recoveredFiles, setRecoveredFiles] = useState<string[]>([]);

  useEffect(() => {
    // Listen for files recovered by C++
    const subscription = DeviceEventEmitter.addListener('onFileRecovered', (filePath: string) => {
      setRecoveredFiles((prev) => [...prev, filePath]);
    });

    return () => subscription.remove();
  }, []);

  const handleCheckRoot = async () => {
    const hasRoot = await NativeRecovery.checkRootAccess();
    setRootStatus(hasRoot);
  };

  const handleStartScan = async () => {
    if (!rootStatus) return;
    setRecoveredFiles([]); // Clear previous list
    setIsScanning(true);
    await NativeRecovery.startDeepScan();
  };

  const handleStopScan = async () => {
    await NativeRecovery.stopScan();
    setIsScanning(false);
  };

  const handleRestore = async (filePath: string) => {
    const success = await NativeRecovery.restoreFile(filePath);
    if (success) {
      Alert.alert("Success", "File restored to Gallery (DCIM/RecoveredMedia)");
      // Remove from list after restore
      setRecoveredFiles((prev) => prev.filter((f) => f !== filePath));
    }
  };

  return (
    <View style={styles.container}>
      <Text>Root Access: {rootStatus === null ? 'Unknown' : rootStatus ? '✅' : '❌'}</Text>
      
      <View style={styles.row}>
        <Button title="Check Root" onPress={handleCheckRoot} />
        <Button
          title={isScanning ? 'Scanning...' : 'Start Deep Scan'}
          onPress={handleStartScan}
          disabled={!rootStatus || isScanning}
        />
        <Button title="Stop" onPress={handleStopScan} disabled={!isScanning} />
      </View>

      <Text style={styles.header}>Recovered Files ({recoveredFiles.length})</Text>
      
      <FlatList
        data={recoveredFiles}
        keyExtractor={(item) => item}
        renderItem={({ item }) => {
          const fileName = item.split('/').pop();
          return (
            <View style={styles.fileItem}>
              <Text>{fileName}</Text>
              <Button title="Restore" onPress={() => handleRestore(item)} />
            </View>
          );
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, paddingTop: 50 },
  row: { flexDirection: 'row', justifyContent: 'space-around', marginVertical: 20 },
  header: { fontSize: 18, fontWeight: 'bold', marginVertical: 10 },
  fileItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 10, borderBottomWidth: 1, borderColor: '#ccc' }
});