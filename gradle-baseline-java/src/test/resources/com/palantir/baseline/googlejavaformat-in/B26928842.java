class B26928842 {
  {
    curr.setData(curr.getData().toBuilder()
        .setPushCertificate(                    // New
            curr.getData().getPushCertficate()) // Old, misspelled
        .clearPushCertficate()                  // Old, misspelled
        .build());
  }
}