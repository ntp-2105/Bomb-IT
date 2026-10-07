import Phaser from "phaser";

class EmptyScene extends Phaser.Scene {
  constructor() {
    super("empty");
  }
}

const game = new Phaser.Game({
  type: Phaser.AUTO,
  parent: "game-container",
  width: 960,
  height: 540,
  backgroundColor: "#111827",
  scale: {
    mode: Phaser.Scale.FIT,
    autoCenter: Phaser.Scale.CENTER_BOTH,
  },
  scene: [EmptyScene],
});

if (import.meta.hot) {
  import.meta.hot.dispose(() => game.destroy(true));
}
