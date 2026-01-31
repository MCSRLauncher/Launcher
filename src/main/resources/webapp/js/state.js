const state = {

  instances: [],
  accounts: [],
  activeAccount: null,
  options: null,
  launcherInfo: null,
  minecraftVersions: null,

  activeTab: 'instances',
  selectedId: null,

  rankedMode: false,
  rankedSelectedId: null,
  rankedData: null,
  rankedMatches: [],
  rankedSeasons: null,

  launching: false,
  launchingInstanceId: null,
  bridgeConnected: false
};

window.state = state;