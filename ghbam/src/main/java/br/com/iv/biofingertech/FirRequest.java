package br.com.iv.biofingertech;

public class FirRequest {
	private String firToVerify;

    public FirRequest(String firToVerify) {
        this.firToVerify = firToVerify;
    }

    public String getFirToVerify() {
        return firToVerify;
    }

    public void setFirToVerify(String firToVerify) {
        this.firToVerify = firToVerify;
    }
}

