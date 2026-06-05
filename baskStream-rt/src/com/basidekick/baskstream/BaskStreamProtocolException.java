package com.basidekick.baskstream;

final class BaskStreamProtocolException extends Exception
{
  private final String code;

  BaskStreamProtocolException(String code, String message)
  {
    super(message);
    this.code = code;
  }

  String getCode()
  {
    return code;
  }
}
