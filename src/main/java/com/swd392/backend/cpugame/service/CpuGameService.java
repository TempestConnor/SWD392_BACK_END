package com.swd392.backend.cpugame.service;

import com.swd392.backend.cpugame.model.CpuGameStart;
import com.swd392.backend.cpugame.model.CpuGameState;
import com.swd392.backend.cpugame.model.CpuTurn;

public interface CpuGameService {

    CpuGameState start(CpuGameStart start);

    CpuGameState playCpuTurn(CpuTurn turn);
}
