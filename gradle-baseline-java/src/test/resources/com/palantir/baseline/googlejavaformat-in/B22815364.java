class B22815364 {
  @Xxx({
      "=0|=0", "Xxx xxxx xx xxxxxxxx xx xxxxxxxxxxxxx.",
      "=0|xxx", "Xxx xxxx x xxxxxxxxxxxx.",
      "xxx|=0", "Xxx xxxx x xxxxxxx.",
      "xxx|xxx", "Xxx xxxx xxx xxxxxxx xxx xxx xxxxxxxxxxxx.",
      "xxxxx|xxx", "Xxx xxxx {0} xxxxxxxx xxx xxx xxxxxxxxxxxx.",
      "xxx|xxxxx", "Xxx xxxx xxx xxxxxxx xxx {1} xxxxxxxxxxxxx."
  })
  int x;

  @Xxx({
    "a", "b",
    "c", "d",
    //
  })
  int y;

  int[] xs = {
    1, 2, 3,
    1, 2, 3,
    1, 2, 3,
  };

  int[] xs = {
    1, 2, 3,
    1, 2, 3,
    1, 2,
  };

  int[] xs = {
    1, 2,
    1,
  };

  int[][] xs = {
    {0b111111111111111111, 0b111111111111111111, 0b111111111111111111, 0b111111111111111111,
        0b111111111111111111, 0b111111111111111111, 0b111111111111111111}, {0b111111111111111111,
        0b111111111111111111, 0b111111111111111111, 0b111111111111111111, 0b111111111111111111,
        0b111111111111111111, 0b111111111111111111},
    {0b111111111111111111, 0b111111111111111111, 0b111111111111111111, 0b111111111111111111,
        0b111111111111111111, 0b111111111111111111, 0b111111111111111111}, {0b111111111111111111,
        0b111111111111111111, 0b111111111111111111, 0b111111111111111111, 0b111111111111111111,
        0b111111111111111111, 0b111111111111111111},
  };
}
