import schedule
from functools import wraps


class Scheduler:
  def __init__(self):
    self.schedule = schedule

  def scheduled(self, *, fixed_rate=None, cron=None):
    def decorator(func):
      @wraps(func)
      def wrapper(*args, **kwargs):
        return func(*args, **kwargs)

      if fixed_rate:
        self.schedule.every(fixed_rate).seconds.do(func)
      elif cron:
        # This is a simplified cron implementation
        # You might want to expand this to handle more complex cron expressions
        self.schedule.every().day.at(cron).do(func)
      else:
        raise ValueError("Either fixed_rate or cron must be specified")

      return wrapper

    return decorator

  def run_pending(self):
    self.schedule.run_pending()


scheduler = Scheduler()
scheduled = scheduler.scheduled
