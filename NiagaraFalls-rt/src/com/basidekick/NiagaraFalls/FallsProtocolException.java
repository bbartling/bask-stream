package com.basidekick.niagarafalls;

final class FallsProtocolException extends Exception
{
  private final String code;

  FallsProtocolException(String code, String message)
  {
    super(message);
    this.code = code;
  }

  String getCode()
  {
    return code;
  }
}
